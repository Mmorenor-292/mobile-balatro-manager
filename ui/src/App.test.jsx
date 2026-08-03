import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import App from "./App";

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

  it("filters the in-app Awesome Balatro directory without pretending source-only entries are installable", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Discover" }));
    await user.click(screen.getByRole("tab", { name: "Awesome Balatro" }));
    expect(screen.getByText("Awesome Balatro directory")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Source only" })).toBeDisabled();
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

  it("supports selecting multiple mods for one reversible action", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(within(screen.getByRole("navigation", { name: "Primary navigation" })).getByRole("button", { name: "Mods" }));
    const selectors = screen.getAllByRole("checkbox", { name: /Select / });
    await user.click(selectors[0]);
    await user.click(selectors[1]);
    expect(screen.getByText("2 selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete selected" })).toBeInTheDocument();
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
