import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import App from "./App";

describe("Balatro AI Assistant", () => {
  beforeEach(() => { delete window.AssistantBridge; });
  afterEach(() => cleanup());

  it("pairs locally without asking for credentials", async () => {
    const user = userEvent.setup();
    render(<App />);
    expect(screen.getByText("GPT-5.6 Terra · High")).toBeInTheDocument();
    expect(screen.queryByText(/API key/i)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Pairing code"), "123456");
    await user.click(screen.getByRole("button", { name: "Pair" }));
    expect(await screen.findByText("Paired")).toBeInTheDocument();
  });

  it("attaches evidence, runs Terra and requires review", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.type(screen.getByLabelText("Pairing code"), "123456");
    await user.click(screen.getByRole("button", { name: "Pair" }));
    await user.click(screen.getByRole("button", { name: "Attach crash log" }));
    expect(screen.getByRole("button", { name: "balatro-crash.log" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Repair incompatibility" }));
    await user.type(screen.getByLabelText("What should I repair or build?"), "Fix the loader crash safely");
    await user.click(screen.getByRole("button", { name: "Run assistant" }));
    expect(screen.getByRole("button", { name: /Terra is inspecting/ })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("dialog", { name: "Review assistant result" })).toBeInTheDocument());
    expect(screen.getByText("Nothing changes without your approval")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export proposed ZIP" })).toBeInTheDocument();
  });
});
