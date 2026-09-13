import { render, screen, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { catalogForMod, hasUpdate, readAppearance } from "./presentation";
beforeEach(() => {
  delete window.AndroidBridge;
  localStorage.clear();
});
describe("Interfaz simplificada", () => {
  it("muestra tres destinos y filtra los mods reales", async () => {
    const user = userEvent.setup();
    render(<App />);
    const nav = within(screen.getByRole("navigation"));
    expect(nav.getAllByRole("button")).toHaveLength(3);
    expect(screen.queryByText(/Import game/)).not.toBeInTheDocument();
    await user.click(nav.getByRole("button", { name: "Mods" }));
    await user.type(screen.getByRole("searchbox"), "Handy");
    expect(screen.getByRole("heading", { name: "Handy" })).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Pokermon" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("switch", { name: "Activar Handy" }));
    expect(
      screen.getByRole("switch", { name: "Activar Handy" }),
    ).not.toBeChecked();
  });
  it("guarda apariencia y consume Atrás al cerrar el panel", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "Abrir apariencia" }));
    await user.click(screen.getByRole("button", { name: "Oscuro" }));
    await user.click(screen.getByRole("switch", { name: "Filtro CRT" }));
    expect(screen.getByRole("slider")).toBeDisabled();
    expect(readAppearance()).toMatchObject({ background: "dark", crt: false });
    const event = new Event("androidback", { cancelable: true });
    act(() => window.dispatchEvent(event));
    expect(event.defaultPrevented).toBe(true);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("no muestra datos de demostración al iniciar Android", () => {
    window.AndroidBridge = { invoke: vi.fn() };
    render(<App />);
    expect(window.AndroidBridge.invoke).toHaveBeenCalledWith("getState", "{}");
    expect(
      screen.getByRole("button", { name: "0 Instalados" }),
    ).toBeInTheDocument();
  });
  it("no convierte versiones ambiguas ni coincidencias múltiples en actualizaciones", () => {
    expect(hasUpdate({ version: "1.9.0" }, { version: "1.10.0" })).toBe(true);
    expect(hasUpdate({ version: "2.0.0" }, { version: "1.9.0" })).toBe(false);
    expect(hasUpdate({ version: "main" }, { version: "beta" })).toBe(false);
    expect(
      catalogForMod({ name: "Handy" }, [{ name: "Handy" }, { name: "Handy" }]),
    ).toBe(null);
  });
  it('envía la versión elegida al motor y respeta su detección', async () => {
    const user = userEvent.setup(); const bridge = vi.fn(); window.AndroidBridge = {invoke:bridge};
    render(<App />);
    act(()=>window.__nativeReceive({connected:true,loading:false,mods:[{folder:'Handy',name:'Handy',version:'1.5.2',hidden:false}],catalog:[{id:'Handy',folderName:'Handy',name:'Handy',source:'BMI',installed:true,installedVersion:'1.5.2',latestVersion:'1.6.0',updateAvailable:true,versions:[{version:'1.6.0',downloadUrl:'https://example.invalid/new.zip'},{version:'1.5.1',downloadUrl:'https://example.invalid/old.zip'}]}]}));
    await user.click(within(screen.getByRole('navigation')).getByRole('button',{name:'Mods'}));
    expect(screen.getByRole('button',{name:'Actualizar Handy'})).toBeInTheDocument();
    await user.click(screen.getByRole('button',{name:'Elegir versión de Handy'}));
    await user.selectOptions(screen.getByRole('combobox',{name:'Versión de Handy'}),'1.5.1');
    await user.click(screen.getByRole('button',{name:'Instalar versión seleccionada'}));
    expect(bridge).toHaveBeenCalledWith('updateCatalogMod',JSON.stringify({id:'Handy',source:'BMI',version:'1.5.1',downloadUrl:'https://example.invalid/old.zip'}));
    expect(hasUpdate({version:'1.0.0'},{version:'2.0.0',updateAvailable:false})).toBe(false);
  });});

