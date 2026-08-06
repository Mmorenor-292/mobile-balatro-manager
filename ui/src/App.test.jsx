import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { mockState } from "./bridge";

describe("MBM - Mobile Balatro Manager public vNext", () => {
  beforeEach(() => {
    delete window.AndroidBridge;
    window.localStorage.clear();
  });

  it("shows the public home with both build routes", async () => {
    render(<App />);
    expect(await screen.findByText("MBM -")).toBeInTheDocument();
    expect(screen.getByText("MOBILE")).toBeInTheDocument();
    expect(screen.getByText("BALATRO MANAGER")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Import game from Steam/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Import from phone APK/ })).toBeInTheDocument();
    expect(screen.queryByText(/files stay local/i)).not.toBeInTheDocument();
  });

  it("walks the Steam route and asks about progress", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /Import game from Steam/ }));
    expect(screen.getByText("Connect your desktop")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Pair desktop" }));
    expect(screen.getByText("Choose the game copy")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("Make it yours")).toBeInTheDocument();
    expect(screen.getByText(/Do you want to import your progress/)).toBeInTheDocument();
  });

  it("shows the exact safe native fallback when unsupported", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /Import from phone APK/ }));
    expect(screen.getByRole("button", { name: "Review detected copy" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Review detected copy" }));
    await user.click(screen.getByRole("button", { name: "See safe fallback" }));
    expect(screen.getByText("This copy cannot be patched safely. Use the Steam/local route instead.")).toBeInTheDocument();
  });

  it("exposes split history tabs and wallpaper selector", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /^History$/ }));
    expect(screen.getByRole("button", { name: "Backup History" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Installation History" }));
    expect(screen.getByText("Handy")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Open settings" }));
    await user.click(screen.getByRole("button", { name: /Blueprint Clean/ }));
    expect(document.documentElement.dataset.wallpaper).toBe("blueprint");
  });

  it("filters the in-app Awesome Balatro directory and installs GitHub archives", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Discover" }));
    await user.click(screen.getByRole("tab", { name: "Awesome Balatro" }));
    expect(screen.getByText("Bunco")).toBeInTheDocument();
    const card = screen.getByText("Bunco").closest("article");
    expect(within(card).getByRole("button", { name: "Install" })).toBeEnabled();
    await user.click(within(card).getByRole("button", { name: "Install" }));
    expect(within(card).getByRole("button", { name: /Installing/ })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/installed at main and enabled/i));
  });

  it("shows installed and latest versions and lets the user choose a release", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Discover" }));
    expect(screen.getAllByText("Installed:").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Latest:").length).toBeGreaterThan(0);
    const handyCard = screen.getByText("Handy").closest("article");
    const versionPicker = screen.getByRole("combobox", { name: "Version for Handy" });
    expect(versionPicker).toHaveValue("1.6.0");
    await user.selectOptions(versionPicker, "1.5.2");
    expect(versionPicker).toHaveValue("1.5.2");
    expect(within(handyCard).getByRole("button", { name: "Reinstall" })).toBeEnabled();
    await user.selectOptions(versionPicker, "1.6.0");
    const update = within(handyCard).getByRole("button", { name: "Update" });
    expect(update).not.toBeDisabled();
    await user.click(update);
    expect(within(handyCard).getByRole("button", { name: /Updating/ })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/updated to 1.6.0 and enabled/i));
  });

  it("uses the four explicit save choices", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /^Saves/ }));
    expect(screen.getByText("Yes, import this profile folder")).toBeInTheDocument();
    expect(screen.getByText("Yes, import all compatible files")).toBeInTheDocument();
    expect(screen.getByText("No, start clean")).toBeInTheDocument();
    expect(screen.getByText("Review files first")).toBeInTheDocument();
  });

  it("supports selecting multiple mods for one explicit action", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Mods" }));
    const selectors = screen.getAllByRole("checkbox", { name: /Select / });
    await user.click(selectors[0]);
    await user.click(selectors[1]);
    expect(screen.getByText("2 selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete selected" })).toBeInTheDocument();
  });

  it("shows per-mod queued feedback without blocking a different mod", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Mods" }));
    window.__nativeReceive({
      ...mockState,
      operations: [{
        token: "op-handy",
        kind: "disable",
        itemId: "HandyBalatro",
        source: "local",
        label: "Disabling mod…",
        status: "running",
        exclusive: false,
        active: true,
      }],
      operation: { active: true, kind: "disable", itemId: "HandyBalatro", source: "local", label: "Disabling mod…", status: "running", exclusive: false },
    });

    expect(await screen.findByText("Disabling mod…")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Disable Handy" })).toBeDisabled();
    const pokermon = screen.getByRole("checkbox", { name: "Disable Pokermon" });
    expect(pokermon).toBeEnabled();
    await user.click(pokermon);
  });

  it("updates every catalog-matched mod with visible progress", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Mods" }));
    const updateAll = screen.getByRole("button", { name: /Update all/ });
    await user.click(updateAll);
    expect(screen.getByRole("button", { name: /Updating 1 of 1/ })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/1 mod was updated successfully/i));
  });

  it("cleans only known junk after an explicit confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Mods" }));
    await user.click(screen.getByRole("button", { name: /Clean junk/ }));
    expect(screen.getByRole("button", { name: /Cleaning/ })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/junk items were permanently removed/i));
  });

  it("exposes local-only diagnostics and bounded history retention controls", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "Open settings" }));
    expect(screen.getByRole("checkbox", { name: /Advanced mode/i })).toBeInTheDocument();
    const crashReports = screen.getByRole("checkbox", { name: /Local crash reports/i });
    expect(crashReports).not.toBeChecked();
    await user.click(crashReports);
    expect(crashReports).toBeChecked();
    const retention = screen.getByRole("combobox", { name: /History retention/i });
    await user.selectOptions(retention, "50");
    expect(retention).toHaveValue("50");
  });
});
