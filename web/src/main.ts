import "./styles.css";
import "@m3e/web/nav-bar";
import "@m3e/web/nav-rail";
import "@m3e/web/form-field";
import "@m3e/web/switch";
import { App } from "./app";
import { registerServiceWorker } from "./push";

const app = new App(document.getElementById("app")!);
void app.start();
void registerServiceWorker();
// Handy in the console and for end-to-end tests.
(window as unknown as { klean: App }).klean = app;
