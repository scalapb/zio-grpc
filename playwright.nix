{ pkgs ? import ./nix/pkgs.nix }:
let
  # Keep this pin in sync with Version.playwright when updating Nixpkgs.
  playwrightVersion = "1.63.0";
  browsers = pkgs.playwright-driver.selectBrowsers {
    withFirefox = false;
    withWebkit = false;
  };
in
assert pkgs.playwright-driver.version == playwrightVersion;
(import ./shell.nix { inherit pkgs; }).overrideAttrs (_: {
  PLAYWRIGHT_BROWSERS_PATH = browsers;
  PLAYWRIGHT_NODEJS_PATH = "${pkgs.nodejs_24}/bin/node";
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";
  # Nix supplies the browser libraries via RPATH rather than ldconfig.
  PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";
})
