using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Diagnostics;
using System.Collections.Concurrent;
using System.IO.Compression;

namespace BmmHelper;

internal sealed record GameCandidate(string Name, string Root, string Executable, string LoveFile, string Version, string Architecture);

internal static class Program
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web) { WriteIndented = true };
    private static readonly object Gate = new();
    private static string PairCode = "";
    private static DateTime PairExpiryUtc;
    private static string PairToken = "";
    private static readonly ConcurrentDictionary<string, BuildJob> BuildJobs = new();
    private static readonly ConcurrentDictionary<string, string> ManualSources = new();
    private static readonly AssistantService Assistant = new();

    public static async Task<int> Main(string[] args)
    {
        var options = ParseArgs(args);
        var candidates = SteamScanner.FindGames(options.SteamRoot);
        var maker = BuilderLocator.Find(options.MakerPath);
        var nativeMaker = NativeBuilderLocator.Find();
        if (options.JsonOnly)
        {
            Console.WriteLine(JsonSerializer.Serialize(new { helper = "BMM.Helper", version = "0.5.0", games = candidates, builderAvailable = maker != null, nativeBuilderAvailable = nativeMaker != null, assistantAvailable = Assistant.CodexAvailable, assistantModel = "gpt-5.6-terra", assistantReasoning = "high", saves = SaveScanner.Summary(), mods = SaveScanner.ModSummary() }, JsonOptions));
            return 0;
        }

        Console.WriteLine("BMM Helper 0.5.0 — LAN-only, allowlisted paths, no cloud upload");
        Console.WriteLine($"Detected Balatro candidates: {candidates.Count}");
        foreach (var game in candidates)
        {
            Console.WriteLine($"  - {game.Root} [{game.Architecture}] {game.Version}");
        }
        Console.WriteLine(maker == null
            ? "Builder: not detected (pairing and manifest only)"
            : $"Builder: {maker}");

        if (options.NoServer)
        {
            return 0;
        }

        using var server = new LocalPairServer(options.Port, candidates, maker, nativeMaker);
        Console.WriteLine($"Pairing endpoint: http://{LocalPairServer.LocalAddress()}:{server.Port}/pair");
        Console.WriteLine("Open BMM on the phone, choose Steam copy, and enter the one-time code below.");
        IssuePairCode();
        Console.WriteLine($"Pairing code: {PairCode} (expires in 10 minutes)");
        Console.WriteLine("Press Ctrl+C to stop the helper.");
        await server.RunAsync();
        return 0;
    }

    private static void IssuePairCode()
    {
        lock (Gate)
        {
            PairCode = RandomNumberGenerator.GetInt32(100000, 999999).ToString();
            PairToken = Convert.ToHexString(RandomNumberGenerator.GetBytes(16));
            PairExpiryUtc = DateTime.UtcNow.AddMinutes(10);
        }
    }

    private static bool Authorize(string? code, out string token)
    {
        lock (Gate)
        {
            token = PairToken;
            return !string.IsNullOrWhiteSpace(code)
                && code == PairCode
                && DateTime.UtcNow < PairExpiryUtc;
        }
    }

    private static Options ParseArgs(string[] args)
    {
        string? root = null;
        var port = 0;
        var json = false;
        var noServer = false;
        string? maker = null;
        for (var i = 0; i < args.Length; i++)
        {
            switch (args[i].ToLowerInvariant())
            {
                case "--steam-root" when i + 1 < args.Length: root = args[++i]; break;
                case "--port" when i + 1 < args.Length && int.TryParse(args[++i], out var parsed): port = parsed; break;
                case "--json": json = true; break;
                case "--no-server": noServer = true; break;
                case "--maker" when i + 1 < args.Length: maker = args[++i]; break;
                case "--help":
                    Console.WriteLine("BMM.Helper.exe [--json] [--no-server] [--steam-root PATH] [--maker PATH] [--port PORT]");
                    Environment.Exit(0);
                    break;
            }
        }
        return new Options(root, port, json, noServer, maker);
    }

    private sealed record Options(string? SteamRoot, int Port, bool JsonOnly, bool NoServer, string? MakerPath);

    private sealed class LocalPairServer : IDisposable
    {
        private readonly TcpListener listener;
        private readonly IReadOnlyList<GameCandidate> games;
        private readonly string? maker;
        private readonly string? nativeMaker;
        private bool stop;

        public LocalPairServer(int requestedPort, IReadOnlyList<GameCandidate> games, string? maker, string? nativeMaker)
        {
            this.games = games;
            this.maker = maker;
            this.nativeMaker = nativeMaker;
            listener = new TcpListener(IPAddress.Any, requestedPort);
            listener.Start();
        }

        public int Port => ((IPEndPoint)listener.LocalEndpoint).Port;
        public static string LocalAddress()
        {
            try
            {
                var host = Dns.GetHostEntry(Dns.GetHostName());
                return host.AddressList.FirstOrDefault(ip => ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))?.ToString() ?? "127.0.0.1";
            }
            catch { return "127.0.0.1"; }
        }

        public async Task RunAsync()
        {
            while (!stop)
            {
                var client = await listener.AcceptTcpClientAsync();
                _ = Task.Run(() => HandleAsync(client));
            }
        }

        private async Task HandleAsync(TcpClient client)
        {
            using (client)
            {
                using var stream = client.GetStream();
                stream.ReadTimeout = 5000;
                var request = await ReadRequestAsync(stream);
                if (request == null) return;
                var uri = new Uri("http://localhost" + request.Target);
                var query = ParseQuery(uri.Query);
                if (uri.AbsolutePath.Equals("/health", StringComparison.OrdinalIgnoreCase))
                {
                    await SendJson(stream, 200, new { ok = true, helper = "BMM.Helper", version = "0.5.0", lanOnly = true, builderAvailable = maker != null, nativeBuilderAvailable = nativeMaker != null, assistantAvailable = Assistant.CodexAvailable });
                    return;
                }
                if (uri.AbsolutePath.Equals("/pair", StringComparison.OrdinalIgnoreCase))
                {
                    IssuePairCode();
                    await SendJson(stream, 200, new { code = PairCode, expiresAtUtc = PairExpiryUtc, address = LocalAddress(), port = Port });
                    return;
                }
                if (!Authorize(query.GetValueOrDefault("code"), out var token) && query.GetValueOrDefault("token") != token)
                {
                    await SendJson(stream, 401, new { error = "Pairing required or expired" });
                    return;
                }
                if (uri.AbsolutePath.Equals("/upload", StringComparison.OrdinalIgnoreCase))
                {
                    if (!string.Equals(request.Method, "POST", StringComparison.OrdinalIgnoreCase))
                    {
                        await SendJson(stream, 405, new { error = "POST required" });
                        return;
                    }
                    await UploadSource(stream, query, token, request.Headers, request.InitialBody);
                    return;
                }
                if (uri.AbsolutePath.Equals("/assistant-upload", StringComparison.OrdinalIgnoreCase))
                {
                    if (!string.Equals(request.Method, "POST", StringComparison.OrdinalIgnoreCase))
                    {
                        await SendJson(stream, 405, new { error = "POST required" });
                        return;
                    }
                    await Assistant.UploadAsync(stream, query, token, request.Headers, request.InitialBody);
                    return;
                }
                if (uri.AbsolutePath.Equals("/assistant-run", StringComparison.OrdinalIgnoreCase))
                {
                    if (!string.Equals(request.Method, "POST", StringComparison.OrdinalIgnoreCase))
                    {
                        await SendJson(stream, 405, new { error = "POST required" });
                        return;
                    }
                    await Assistant.StartAsync(stream, query, token, request.Headers, request.InitialBody);
                    return;
                }
                if (!string.Equals(request.Method, "GET", StringComparison.OrdinalIgnoreCase))
                {
                    await SendJson(stream, 405, new { error = "GET only" });
                    return;
                }
                if (uri.AbsolutePath.Equals("/manifest", StringComparison.OrdinalIgnoreCase))
                {
                    var manualReady = ManualSources.TryGetValue(token, out var manualPath) && File.Exists(manualPath);
                    await SendJson(stream, 200, new { token, games, builderAvailable = maker != null, nativeBuilderAvailable = nativeMaker != null, assistantAvailable = Assistant.CodexAvailable, assistantModel = "gpt-5.6-terra", assistantReasoning = "high", manualUploadReady = manualReady, saves = SaveScanner.Summary(), mods = SaveScanner.ModSummary(), allowlist = games.Select(g => g.Root).ToArray(), next = nativeMaker != null ? "The personal Play Store builder is ready. MBM can upload an installed source APK and build a separate mod-capable copy." : maker == null ? "Install the Balatro Mobile Maker and restart the helper to enable local builds." : manualReady ? "Use /build?token=...&game=-1 to build the uploaded source." : "Use /build?token=...&game=0 after reviewing the detected copy, or upload a .love/.zip source." });
                    return;
                }
                if (uri.AbsolutePath.Equals("/assistant-status", StringComparison.OrdinalIgnoreCase))
                {
                    await Assistant.StatusAsync(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/assistant-artifact", StringComparison.OrdinalIgnoreCase))
                {
                    await Assistant.ArtifactAsync(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/build", StringComparison.OrdinalIgnoreCase))
                {
                    await StartBuild(stream, query, token);
                    return;
                }
                if (uri.AbsolutePath.Equals("/build-status", StringComparison.OrdinalIgnoreCase))
                {
                    await BuildStatus(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/build-artifact", StringComparison.OrdinalIgnoreCase))
                {
                    await BuildArtifact(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/save-archive", StringComparison.OrdinalIgnoreCase))
                {
                    await SaveArchive(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/mods-archive", StringComparison.OrdinalIgnoreCase))
                {
                    await ModsArchive(stream, query);
                    return;
                }
                if (uri.AbsolutePath.Equals("/file", StringComparison.OrdinalIgnoreCase))
                {
                    await SendSelectedFile(stream, query.GetValueOrDefault("path"), games);
                    return;
                }
                await SendJson(stream, 404, new { error = "Unknown endpoint" });
            }
        }

        private sealed record IncomingRequest(string Method, string Target, IReadOnlyDictionary<string, string> Headers, byte[] InitialBody);

        private static async Task<IncomingRequest?> ReadRequestAsync(NetworkStream stream)
        {
            var bytes = new MemoryStream();
            var buffer = new byte[8192];
            var headerEnd = -1;
            while (bytes.Length < 32 * 1024)
            {
                var read = await stream.ReadAsync(buffer.AsMemory(0, buffer.Length));
                if (read == 0) break;
                bytes.Write(buffer, 0, read);
                var current = bytes.GetBuffer();
                headerEnd = FindHeaderEnd(current, (int)bytes.Length);
                if (headerEnd >= 0) break;
            }
            if (headerEnd < 0) return null;
            var all = bytes.ToArray();
            var headerText = Encoding.ASCII.GetString(all, 0, headerEnd);
            var lines = headerText.Split("\r\n", StringSplitOptions.None);
            var first = lines.FirstOrDefault() ?? "";
            var parts = first.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2) return null;
            var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            foreach (var line in lines.Skip(1))
            {
                var separator = line.IndexOf(':');
                if (separator <= 0) continue;
                headers[line[..separator].Trim()] = line[(separator + 1)..].Trim();
            }
            var bodyStart = headerEnd + 4;
            var initialBody = bodyStart < all.Length ? all[bodyStart..] : Array.Empty<byte>();
            return new IncomingRequest(parts[0], parts[1], headers, initialBody);
        }

        private static int FindHeaderEnd(byte[] bytes, int length)
        {
            for (var i = 3; i < length; i++)
            {
                if (bytes[i - 3] == '\r' && bytes[i - 2] == '\n' && bytes[i - 1] == '\r' && bytes[i] == '\n') return i - 3;
            }
            return -1;
        }

        private static async Task UploadSource(NetworkStream stream, Dictionary<string, string> query, string token, IReadOnlyDictionary<string, string> headers, byte[] initialBody)
        {
            if (!headers.TryGetValue("Content-Length", out var rawLength) || !long.TryParse(rawLength, out var length) || length <= 0)
            {
                await SendJson(stream, 411, new { error = "Content-Length is required for a bounded upload." });
                return;
            }
            if (length > 500L * 1024 * 1024)
            {
                await SendJson(stream, 413, new { error = "Uploaded source exceeds the 500 MB safety limit." });
                return;
            }
            var requestedName = query.GetValueOrDefault("name") ?? "source.love";
            var name = Path.GetFileName(Uri.UnescapeDataString(requestedName));
            var extension = Path.GetExtension(name).ToLowerInvariant();
            if (extension is not ".love" and not ".zip" and not ".apk")
            {
                await SendJson(stream, 415, new { error = "Upload a .love/.zip source or the user's Play Store base APK." });
                return;
            }
            var folder = Path.Combine(Path.GetTempPath(), "bmm-upload-" + token);
            Directory.CreateDirectory(folder);
            var target = Path.Combine(folder, extension switch
            {
                ".love" => "Balatro.love",
                ".apk" => "Balatro-PlayStore.apk",
                _ => "Balatro.zip"
            });
            if (ManualSources.TryRemove(token, out var previous))
            {
                try { File.Delete(previous); } catch { }
            }
            try
            {
                await using var output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None);
                var first = (int)Math.Min(length, initialBody.LongLength);
                if (first > 0) await output.WriteAsync(initialBody.AsMemory(0, first));
                var remaining = length - first;
                var buffer = new byte[32 * 1024];
                while (remaining > 0)
                {
                    var read = await stream.ReadAsync(buffer.AsMemory(0, (int)Math.Min(buffer.Length, remaining)));
                    if (read == 0) throw new EndOfStreamException("The upload ended before Content-Length was received.");
                    await output.WriteAsync(buffer.AsMemory(0, read));
                    remaining -= read;
                }
                ManualSources[token] = target;
                await SendJson(stream, 200, new { uploaded = true, name, bytes = length, game = extension == ".apk" ? -2 : -1, kind = extension == ".apk" ? "playstore" : "source" });
            }
            catch (Exception error)
            {
                try { File.Delete(target); } catch { }
                await SendJson(stream, 400, new { error = "The source upload failed: " + error.Message });
            }
        }

        private async Task StartBuild(NetworkStream stream, Dictionary<string, string> query, string token)
        {
            var nativeMode = string.Equals(query.GetValueOrDefault("native"), "1", StringComparison.OrdinalIgnoreCase)
                || string.Equals(query.GetValueOrDefault("mode"), "native", StringComparison.OrdinalIgnoreCase)
                || string.Equals(query.GetValueOrDefault("mode"), "playstore", StringComparison.OrdinalIgnoreCase);
            if (nativeMode)
            {
                if (nativeMaker == null)
                {
                    await SendJson(stream, 409, new { error = "The Play Store personal-build tool is not installed beside BMM.Helper.exe.", setup = "Keep the bundled native-maker folder beside BMM.Helper.exe and restart the helper." });
                    return;
                }
                if (!ManualSources.TryGetValue(token, out var nativeSource) || !File.Exists(nativeSource)
                    || !string.Equals(Path.GetExtension(nativeSource), ".apk", StringComparison.OrdinalIgnoreCase))
                {
                    await SendJson(stream, 409, new { error = "Upload the installed Play Store base APK before starting the personal build." });
                    return;
                }
                var nativeId = Guid.NewGuid().ToString("N");
                var nativeWorkspace = Path.Combine(Path.GetTempPath(), "bmm-build-" + nativeId);
                Directory.CreateDirectory(nativeWorkspace);
                var nativeJob = new BuildJob(nativeId, nativeWorkspace, "queued", "", "", DateTime.UtcNow);
                BuildJobs[nativeId] = nativeJob;
                _ = Task.Run(() => RunNativeBuild(nativeJob, nativeMaker, nativeSource));
                await SendJson(stream, 202, new { jobId = nativeId, status = "queued", token, kind = "playstore-personal" });
                return;
            }
            if (maker == null)
            {
                await SendJson(stream, 409, new { error = "The portable Balatro Mobile Maker was not detected on this PC.", setup = "Place the maker executable next to BMM.Helper.exe or pass --maker PATH, then restart the helper." });
                return;
            }
            if (!int.TryParse(query.GetValueOrDefault("game"), out var index) || (index < 0 && index != -1) || index >= games.Count)
            {
                await SendJson(stream, 400, new { error = "Select a detected game or upload a .love/.zip source first." });
                return;
            }
            string input;
            if (index == -1)
            {
                if (!ManualSources.TryGetValue(token, out var uploaded) || !File.Exists(uploaded))
                {
                    await SendJson(stream, 409, new { error = "Upload a .love or .zip source before using the manual build route." });
                    return;
                }
                input = uploaded;
            }
            else
            {
                var game = games[index];
                input = File.Exists(game.Executable) ? game.Executable : game.LoveFile;
            }
            if (string.IsNullOrWhiteSpace(input) || !File.Exists(input))
            {
                await SendJson(stream, 409, new { error = "The selected source has no executable or .love file." });
                return;
            }
            var id = Guid.NewGuid().ToString("N");
            var workspace = Path.Combine(Path.GetTempPath(), "bmm-build-" + id);
            Directory.CreateDirectory(workspace);
            // Balatro Mobile Maker looks for these exact filenames in its working
            // directory. Keep the input inside the isolated workspace and never
            // expose the original Steam path to the external builder.
            var target = Path.Combine(workspace, Path.GetExtension(input).Equals(".love", StringComparison.OrdinalIgnoreCase) ? "Balatro.love" : "Balatro.exe");
            File.Copy(input, target, true);
            var job = new BuildJob(id, workspace, "queued", "", "", DateTime.UtcNow);
            BuildJobs[id] = job;
            _ = Task.Run(() => RunBuild(job, maker));
            await SendJson(stream, 202, new { jobId = id, status = "queued", token });
        }

        private async Task BuildStatus(NetworkStream stream, Dictionary<string, string> query)
        {
            var id = query.GetValueOrDefault("id") ?? "";
            if (!BuildJobs.TryGetValue(id, out var job)) { await SendJson(stream, 404, new { error = "Build job not found." }); return; }
            await SendJson(stream, 200, new { jobId = job.Id, status = job.Status, log = job.Log, error = job.Error, artifactReady = File.Exists(Path.Combine(job.Workspace, "balatro.apk")) });
        }

        private async Task BuildArtifact(NetworkStream stream, Dictionary<string, string> query)
        {
            var id = query.GetValueOrDefault("id") ?? "";
            if (!BuildJobs.TryGetValue(id, out var job)) { await SendJson(stream, 404, new { error = "Build job not found." }); return; }
            var artifact = Path.Combine(job.Workspace, "balatro.apk");
            if (!File.Exists(artifact) || !string.Equals(job.Status, "completed", StringComparison.OrdinalIgnoreCase)) { await SendJson(stream, 409, new { error = "The verified APK is not ready." }); return; }
            var info = new FileInfo(artifact);
            var header = Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: {info.Length}\r\nContent-Disposition: attachment; filename=balatro-bmm.apk\r\nConnection: close\r\n\r\n");
            await stream.WriteAsync(header);
            await using var input = File.OpenRead(artifact);
            await input.CopyToAsync(stream);
        }

        private static async Task RunBuild(BuildJob job, string maker)
        {
            job.Status = "running";
            job.Log = "Starting the external Balatro Mobile Maker in an isolated temporary workspace.";
            try
            {
                var start = new ProcessStartInfo
                {
                    FileName = maker,
                    WorkingDirectory = job.Workspace,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                using var process = Process.Start(start) ?? throw new InvalidOperationException("Could not start the maker executable.");
                var output = new StringBuilder();
                process.OutputDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) output.AppendLine(e.Data); };
                process.ErrorDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) output.AppendLine(e.Data); };
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                // The upstream maker is intentionally interactive. These answers select
                // Android, mobile-safe patches, 60 FPS, and no automatic device/save transfer.
                await process.StandardInput.WriteAsync("y\nn\ny\nn\ny\n60\ny\ny\ny\nn\nn\nn\n");
                await process.StandardInput.FlushAsync();
                await process.WaitForExitAsync().WaitAsync(TimeSpan.FromMinutes(45));
                job.Log = output.ToString().Trim();
                var artifact = Path.Combine(job.Workspace, "balatro.apk");
                // The upstream maker calls Console.ReadKey() at the very end. With
                // redirected stdin this can produce a non-zero exit code even after
                // a complete APK was generated. Trust the artifact only after a
                // bounded APK structure check, and retain the exit code in the log.
                if (IsValidApk(artifact))
                {
                    if (process.ExitCode != 0)
                    {
                        job.Log = (job.Log + Environment.NewLine + $"Maker exited with code {process.ExitCode} after producing a valid APK; treating the build as complete.").Trim();
                    }
                    job.Status = "completed";
                    return;
                }
                if (process.ExitCode != 0 || !File.Exists(artifact))
                {
                    job.Status = "failed";
                    job.Error = "The maker did not produce a verified APK. Review the build log and run the maker directly if it needs a new prompt sequence.";
                    return;
                }
                job.Status = "completed";
            }
            catch (Exception error)
            {
                job.Status = "failed";
                job.Error = error.Message;
            }
        }

        private static async Task RunNativeBuild(BuildJob job, string makerRoot, string source)
        {
            job.Status = "running";
            job.Log = "Starting the personal Play Store build in an isolated temporary workspace.";
            try
            {
                var workspace = Path.Combine(job.Workspace, "native-maker");
                CopyDirectory(makerRoot, workspace);
                var sourceCopy = Path.Combine(job.Workspace, "Balatro-PlayStore.apk");
                File.Copy(source, sourceCopy, true);
                var python = NativeBuilderLocator.FindPython(workspace)
                    ?? throw new InvalidOperationException("Bundled Python was not found for the native builder.");
                var buildScript = Path.Combine(workspace, "build.py");
                if (!File.Exists(buildScript)) throw new InvalidOperationException("Native builder script is missing.");
                var start = new ProcessStartInfo
                {
                    FileName = python,
                    WorkingDirectory = workspace,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                start.ArgumentList.Add("build.py");
                start.ArgumentList.Add("--balatro");
                start.ArgumentList.Add(sourceCopy);
                start.ArgumentList.Add("--disable-crt");
                start.ArgumentList.Add("--readabletro");
                start.ArgumentList.Add("--no-ios");
                start.ArgumentList.Add("--force");
                start.ArgumentList.Add("--steamodded");
                start.ArgumentList.Add("latest");
                using var process = Process.Start(start) ?? throw new InvalidOperationException("Could not start the native builder.");
                var output = new StringBuilder();
                process.OutputDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) output.AppendLine(e.Data); };
                process.ErrorDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) output.AppendLine(e.Data); };
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                await process.StandardInput.WriteAsync("\n");
                await process.StandardInput.FlushAsync();
                await process.WaitForExitAsync().WaitAsync(TimeSpan.FromMinutes(60));
                job.Log = output.ToString().Trim();
                var generated = Path.Combine(workspace, "balatro-mobile-maker", "balatro-aligned-debugSigned.apk");
                var artifact = Path.Combine(job.Workspace, "balatro.apk");
                if (IsValidApk(generated))
                {
                    File.Copy(generated, artifact, true);
                    job.Status = "completed";
                    if (process.ExitCode != 0)
                    {
                        job.Log = (job.Log + Environment.NewLine + $"Native builder exited with code {process.ExitCode} after producing a valid APK; treating the build as complete.").Trim();
                    }
                    return;
                }
                job.Status = "failed";
                job.Error = "The personal Play Store builder did not produce a verified APK. Review the build log.";
            }
            catch (Exception error)
            {
                job.Status = "failed";
                job.Error = error.Message;
            }
        }

        private static void CopyDirectory(string source, string destination)
        {
            Directory.CreateDirectory(destination);
            foreach (var file in Directory.EnumerateFiles(source, "*", SearchOption.TopDirectoryOnly))
            {
                File.Copy(file, Path.Combine(destination, Path.GetFileName(file)), true);
            }
            foreach (var directory in Directory.EnumerateDirectories(source, "*", SearchOption.TopDirectoryOnly))
            {
                CopyDirectory(directory, Path.Combine(destination, Path.GetFileName(directory)));
            }
        }

        private static bool IsValidApk(string path)
        {
            try
            {
                var info = new FileInfo(path);
                if (!info.Exists || info.Length < 16 * 1024) return false;
                using var archive = ZipFile.OpenRead(path);
                return archive.GetEntry("AndroidManifest.xml") != null;
            }
            catch
            {
                return false;
            }
        }

        private async Task SaveArchive(NetworkStream stream, Dictionary<string, string> query)
        {
            var profile = query.GetValueOrDefault("profile") ?? "";
            string? archive = null;
            try
            {
                archive = SaveScanner.CreateArchive(profile);
                if (archive == null)
                {
                    await SendJson(stream, 404, new { error = "No compatible Balatro save folder was found under %APPDATA%\\Balatro." });
                    return;
                }
                var info = new FileInfo(archive);
                var header = Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: {info.Length}\r\nContent-Disposition: attachment; filename=balatro-steam-saves.zip\r\nConnection: close\r\n\r\n");
                await stream.WriteAsync(header);
                await using var input = File.OpenRead(archive);
                await input.CopyToAsync(stream);
            }
            catch (Exception error)
            {
                await SendJson(stream, 400, new { error = "The save archive could not be prepared: " + error.Message });
            }
            finally
            {
                if (archive != null) try { File.Delete(archive); } catch { }
            }
        }

        private async Task ModsArchive(NetworkStream stream, Dictionary<string, string> query)
        {
            string? archive = null;
            try
            {
                archive = SaveScanner.CreateModsArchive();
                if (archive == null)
                {
                    await SendJson(stream, 404, new { error = "No mod folders were found under %APPDATA%\\Balatro\\Mods." });
                    return;
                }
                var info = new FileInfo(archive);
                var header = Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: {info.Length}\r\nContent-Disposition: attachment; filename=balatro-desktop-mods.zip\r\nConnection: close\r\n\r\n");
                await stream.WriteAsync(header);
                await using var input = File.OpenRead(archive);
                await input.CopyToAsync(stream);
            }
            catch (Exception error)
            {
                await SendJson(stream, 400, new { error = "The desktop mod archive could not be prepared: " + error.Message });
            }
            finally
            {
                if (archive != null) try { File.Delete(archive); } catch { }
            }
        }

        private static Dictionary<string, string> ParseQuery(string query)
        {
            return query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries)
                .Select(part => part.Split('=', 2))
                .Where(pair => pair.Length == 2)
                .ToDictionary(pair => Uri.UnescapeDataString(pair[0]), pair => Uri.UnescapeDataString(pair[1]), StringComparer.OrdinalIgnoreCase);
        }

        private static async Task SendJson(NetworkStream stream, int status, object payload)
        {
            var body = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload, JsonOptions));
            var header = Encoding.ASCII.GetBytes($"HTTP/1.1 {status} {StatusText(status)}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
            await stream.WriteAsync(header);
            await stream.WriteAsync(body);
        }

        private static async Task SendSelectedFile(NetworkStream stream, string? rawPath, IReadOnlyList<GameCandidate> games)
        {
            if (string.IsNullOrWhiteSpace(rawPath))
            {
                await SendJson(stream, 400, new { error = "A selected path is required." });
                return;
            }
            string path;
            try { path = Path.GetFullPath(rawPath); } catch { await SendJson(stream, 400, new { error = "Invalid path." }); return; }
            var allowed = games.Any(game => IsInside(path, game.Root));
            if (!allowed || !File.Exists(path))
            {
                await SendJson(stream, 403, new { error = "Path is outside the approved Balatro installation." });
                return;
            }
            var info = new FileInfo(path);
            if (info.Length > 500L * 1024 * 1024)
            {
                await SendJson(stream, 413, new { error = "Selected file exceeds the 500 MB safety limit." });
                return;
            }
            var header = Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: {info.Length}\r\nContent-Disposition: attachment; filename=\"{Uri.EscapeDataString(info.Name)}\"\r\nConnection: close\r\n\r\n");
            await stream.WriteAsync(header);
            await using var input = File.OpenRead(path);
            await input.CopyToAsync(stream);
        }

        private static bool IsInside(string child, string root)
        {
            var normalizedChild = Path.GetFullPath(child).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            var normalizedRoot = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            return normalizedChild.StartsWith(normalizedRoot, StringComparison.OrdinalIgnoreCase);
        }

        private static string StatusText(int status) => status switch { 200 => "OK", 202 => "Accepted", 400 => "Bad Request", 401 => "Unauthorized", 404 => "Not Found", 405 => "Method Not Allowed", 409 => "Conflict", 411 => "Length Required", 413 => "Payload Too Large", 415 => "Unsupported Media Type", _ => "Error" };
        public void Dispose() { stop = true; listener.Stop(); }
    }
}

internal sealed class BuildJob
{
    public BuildJob(string id, string workspace, string status, string log, string error, DateTime createdUtc) { Id = id; Workspace = workspace; Status = status; Log = log; Error = error; CreatedUtc = createdUtc; }
    public string Id { get; }
    public string Workspace { get; }
    public string Status { get; set; }
    public string Log { get; set; }
    public string Error { get; set; }
    public DateTime CreatedUtc { get; }
}

internal static class BuilderLocator
{
    public static string? Find(string? explicitPath)
    {
        var candidates = new List<string>();
        if (!string.IsNullOrWhiteSpace(explicitPath)) candidates.Add(explicitPath);
        var baseDir = AppContext.BaseDirectory;
        candidates.AddRange(new[] { "balatro-mobile-maker.exe", "BalatroMobileMaker.exe", "BMM.BalatroMobileMaker.exe" }.Select(name => Path.Combine(baseDir, name)));
        return candidates.Select(path => { try { return Path.GetFullPath(path); } catch { return ""; } })
            .FirstOrDefault(path => File.Exists(path));
    }
}

internal static class NativeBuilderLocator
{
    public static string? Find()
    {
        var root = Path.Combine(AppContext.BaseDirectory, "native-maker");
        return File.Exists(Path.Combine(root, "build.py")) && FindPython(root) != null ? root : null;
    }

    public static string? FindPython(string root)
    {
        var bundled = Path.Combine(root, "python", "python.exe");
        if (File.Exists(bundled)) return bundled;
        var path = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (var folder in path.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
        {
            var candidate = Path.Combine(folder.Trim(), "python.exe");
            if (File.Exists(candidate)) return candidate;
        }
        return null;
    }
}

internal static class SteamScanner
{
    private static readonly Regex PathLine = new("\\\"path\\\"\\s+\\\"(?<path>[^\\\"]+)\\\"", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly string[] CommonRoots =
    {
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData)
    };

    public static IReadOnlyList<GameCandidate> FindGames(string? explicitRoot)
    {
        var roots = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        if (!string.IsNullOrWhiteSpace(explicitRoot)) roots.Add(Path.GetFullPath(explicitRoot));
        foreach (var common in CommonRoots.Where(path => !string.IsNullOrWhiteSpace(path)))
        {
            roots.Add(Path.Combine(common, "Steam"));
            roots.Add(Path.Combine(common, "SteamLibrary"));
        }
        foreach (var root in roots.ToArray())
        {
            var vdf = Path.Combine(root, "steamapps", "libraryfolders.vdf");
            if (!File.Exists(vdf)) continue;
            foreach (Match match in PathLine.Matches(File.ReadAllText(vdf)))
            {
                var path = match.Groups["path"].Value.Replace("\\\\", "\\");
                if (!string.IsNullOrWhiteSpace(path)) roots.Add(path);
            }
        }
        var games = new List<GameCandidate>();
        foreach (var library in roots)
        {
            var balatro = Path.Combine(library, "steamapps", "common", "Balatro");
            if (!Directory.Exists(balatro)) continue;
            var exe = Directory.EnumerateFiles(balatro, "*.exe", SearchOption.TopDirectoryOnly).FirstOrDefault() ?? "";
            var love = Directory.EnumerateFiles(balatro, "*.love", SearchOption.TopDirectoryOnly).FirstOrDefault() ?? "";
            var version = DetectVersion(balatro, exe);
            var architecture = DetectArchitecture(exe);
            games.Add(new GameCandidate("Balatro", balatro, exe, love, version, architecture));
        }
        return games.DistinctBy(game => game.Root, StringComparer.OrdinalIgnoreCase).ToArray();
    }

    private static string DetectVersion(string root, string executable)
    {
        var versionFile = Path.Combine(root, "version.txt");
        if (File.Exists(versionFile))
        {
            var value = File.ReadAllText(versionFile).Trim();
            if (!string.IsNullOrWhiteSpace(value)) return value;
        }
        if (!string.IsNullOrWhiteSpace(executable) && File.Exists(executable))
        {
            try
            {
                var info = FileVersionInfo.GetVersionInfo(executable);
                var value = info.ProductVersion ?? info.FileVersion;
                if (!string.IsNullOrWhiteSpace(value)) return value.Trim();
            }
            catch { }
        }
        return "unknown";
    }

    private static string DetectArchitecture(string executable)
    {
        if (string.IsNullOrWhiteSpace(executable) || !File.Exists(executable)) return "unknown";
        try
        {
            using var stream = File.OpenRead(executable);
            using var reader = new BinaryReader(stream);
            if (reader.ReadUInt16() != 0x5A4D) return "unknown";
            stream.Position = 0x3C;
            var peOffset = reader.ReadInt32();
            if (peOffset < 0 || peOffset > stream.Length - 6) return "unknown";
            stream.Position = peOffset;
            if (reader.ReadUInt32() != 0x00004550) return "unknown";
            return reader.ReadUInt16() switch
            {
                0x014c => "windows-x86",
                0x8664 => "windows-x64",
                0xAA64 => "windows-arm64",
                _ => "windows-unknown"
            };
        }
        catch { return "unknown"; }
    }
}

/// <summary>
/// Bounded, read-only inspection of the user's Windows Balatro data directory.
/// This deliberately stays separate from the Steam game allowlist: mods and
/// saves are per-user data under %APPDATA% and are never copied into Drive.
/// </summary>
internal static class SaveScanner
{
    private const long MaxBytes = 100L * 1024L * 1024L;
    private const int MaxFiles = 500;
    private static string Root => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Balatro");
    private static string Mods => Path.Combine(Root, "Mods");

    public static object Summary()
    {
        var profiles = new List<string>();
        var files = 0;
        if (Directory.Exists(Root))
        {
            foreach (var directory in Directory.EnumerateDirectories(Root, "*", SearchOption.TopDirectoryOnly))
            {
                var name = Path.GetFileName(directory);
                if (Regex.IsMatch(name, "^\\d+$")) profiles.Add(name);
            }
            files = CountFilesBounded(Root);
        }
        profiles.Sort(StringComparer.OrdinalIgnoreCase);
        return new { available = Directory.Exists(Root) && files > 0, files, profiles, source = "%APPDATA%/Balatro" };
    }

    public static object ModSummary()
    {
        var folders = new List<string>();
        var frameworks = new List<string>();
        var files = 0;
        if (Directory.Exists(Mods))
        {
            foreach (var directory in Directory.EnumerateDirectories(Mods, "*", SearchOption.TopDirectoryOnly))
            {
                var name = Path.GetFileName(directory);
                folders.Add(name);
                if (Regex.IsMatch(name, "steamodded|smods|lovely", RegexOptions.IgnoreCase)) frameworks.Add(name);
            }
            files = CountFilesBounded(Mods);
        }
        folders.Sort(StringComparer.OrdinalIgnoreCase);
        frameworks.Sort(StringComparer.OrdinalIgnoreCase);
        return new { available = Directory.Exists(Mods), folders, frameworks, files, source = "%APPDATA%/Balatro/Mods" };
    }

    public static string? CreateArchive(string profile)
    {
        if (!Directory.Exists(Root)) return null;
        var source = string.IsNullOrWhiteSpace(profile) ? Root : Path.Combine(Root, profile);
        if (!Directory.Exists(source)) return null;
        var files = CountFilesBounded(source);
        if (files == 0) return null;
        if (files > MaxFiles) throw new InvalidDataException("The save folder contains too many files.");
        var archive = Path.Combine(Path.GetTempPath(), "bmm-save-" + Guid.NewGuid().ToString("N") + ".zip");
        try
        {
            using var zip = ZipFile.Open(archive, ZipArchiveMode.Create);
            long bytes = 0;
            var count = 0;
            AddDirectory(zip, source, "", ref count, ref bytes);
            return archive;
        }
        catch
        {
            try { File.Delete(archive); } catch { }
            throw;
        }
    }

    public static string? CreateModsArchive()
    {
        if (!Directory.Exists(Mods)) return null;
        var folders = Directory.EnumerateDirectories(Mods, "*", SearchOption.TopDirectoryOnly).ToArray();
        if (folders.Length == 0) return null;
        var archive = Path.Combine(Path.GetTempPath(), "bmm-mods-" + Guid.NewGuid().ToString("N") + ".zip");
        try
        {
            using var zip = ZipFile.Open(archive, ZipArchiveMode.Create);
            long bytes = 0;
            var count = 0;
            foreach (var folder in folders)
            {
                var name = Path.GetFileName(folder).Replace('\\', '_').Replace('/', '_');
                AddModDirectory(zip, folder, name, ref count, ref bytes);
            }
            if (count == 0) { File.Delete(archive); return null; }
            return archive;
        }
        catch
        {
            try { File.Delete(archive); } catch { }
            throw;
        }
    }

    private static int CountFilesBounded(string directory)
    {
        var count = 0;
        CountDirectory(directory, ref count);
        return count;
    }

    private static void CountDirectory(string directory, ref int count)
    {
        if (count > MaxFiles) return;
        try
        {
            foreach (var file in Directory.EnumerateFiles(directory, "*", SearchOption.TopDirectoryOnly))
            {
                if (++count > MaxFiles) return;
            }
            foreach (var child in Directory.EnumerateDirectories(directory, "*", SearchOption.TopDirectoryOnly))
            {
                if (string.Equals(Path.GetFullPath(child).TrimEnd(Path.DirectorySeparatorChar), Path.GetFullPath(Mods).TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase)) continue;
                CountDirectory(child, ref count);
                if (count > MaxFiles) return;
            }
        }
        catch (UnauthorizedAccessException) { }
        catch (IOException) { }
    }

    private static void AddDirectory(ZipArchive zip, string directory, string prefix, ref int count, ref long bytes)
    {
        foreach (var file in Directory.EnumerateFiles(directory, "*", SearchOption.TopDirectoryOnly))
        {
            if (++count > MaxFiles) throw new InvalidDataException("The save folder contains too many files.");
            var name = Path.GetFileName(file).Replace('\\', '_').Replace('/', '_');
            var entryName = string.IsNullOrWhiteSpace(prefix) ? name : prefix.TrimEnd('/') + "/" + name;
            var entry = zip.CreateEntry(entryName, CompressionLevel.Fastest);
            using var input = File.OpenRead(file);
            using var output = entry.Open();
            var buffer = new byte[16 * 1024];
            int read;
            while ((read = input.Read(buffer, 0, buffer.Length)) != 0)
            {
                bytes += read;
                if (bytes > MaxBytes) throw new InvalidDataException("The save archive exceeds the 100 MB safety limit.");
                output.Write(buffer, 0, read);
            }
        }
        foreach (var child in Directory.EnumerateDirectories(directory, "*", SearchOption.TopDirectoryOnly))
        {
            var name = Path.GetFileName(child).Replace('\\', '_').Replace('/', '_');
            if (string.Equals(child, Mods, StringComparison.OrdinalIgnoreCase)) continue;
            var childPrefix = string.IsNullOrWhiteSpace(prefix) ? name : prefix.TrimEnd('/') + "/" + name;
            AddDirectory(zip, child, childPrefix, ref count, ref bytes);
        }
    }

    private static void AddModDirectory(ZipArchive zip, string directory, string prefix, ref int count, ref long bytes)
    {
        foreach (var file in Directory.EnumerateFiles(directory, "*", SearchOption.TopDirectoryOnly))
        {
            if (++count > 20_000) throw new InvalidDataException("The Mods folder contains too many files.");
            var name = Path.GetFileName(file).Replace('\\', '_').Replace('/', '_');
            var lower = name.ToLowerInvariant();
            if (lower.EndsWith(".exe") || lower.EndsWith(".dll") || lower.EndsWith(".so") || lower.EndsWith(".dylib") || lower.EndsWith(".bat") || lower.EndsWith(".cmd") || lower.EndsWith(".ps1") || lower.EndsWith(".apk"))
                throw new InvalidDataException("The Mods folder contains a desktop-only file: " + name);
            var entryName = prefix.TrimEnd('/') + "/" + name;
            var entry = zip.CreateEntry(entryName, CompressionLevel.Fastest);
            using var input = File.OpenRead(file);
            using var output = entry.Open();
            var buffer = new byte[16 * 1024];
            int read;
            while ((read = input.Read(buffer, 0, buffer.Length)) != 0)
            {
                bytes += read;
                if (bytes > 250L * 1024L * 1024L) throw new InvalidDataException("The Mods archive exceeds the 250 MB safety limit.");
                output.Write(buffer, 0, read);
            }
        }
        foreach (var child in Directory.EnumerateDirectories(directory, "*", SearchOption.TopDirectoryOnly))
        {
            var name = Path.GetFileName(child).Replace('\\', '_').Replace('/', '_');
            AddModDirectory(zip, child, prefix.TrimEnd('/') + "/" + name, ref count, ref bytes);
        }
    }
}
