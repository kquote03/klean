{
  description = "Klean dev shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
      buildTools = "36.0.0"; # AGP 9.5 default build-tools
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.0" "36" ];
        buildToolsVersions = [ buildTools ];
        includeEmulator = false;
        includeSystemImages = false;
        includeNDK = false;
      };
      sdk = android.androidsdk;
      sdkRoot = "${sdk}/libexec/android-sdk";
      defaultShell = pkgs.mkShell {
        # nodejs_22 + pnpm: the web client (web/, M26).
        packages = [ sdk pkgs.jdk21 pkgs.git pkgs.curl pkgs.sqlite pkgs.nodejs_22 pkgs.pnpm ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk21.home;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildTools}/aapt2";
      };
    in {
      devShells.${system} = {
        default = defaultShell;
        # The web client's end-to-end and visual tests: `nix develop .#web -c pnpm -C web e2e`.
        # Playwright's browsers come from nixpkgs (the npm ones don't run on NixOS); web/package.json pins
        # @playwright/test to the same version as this playwright-driver.
        web = defaultShell.overrideAttrs (old: {
          PLAYWRIGHT_BROWSERS_PATH = "${pkgs.playwright-driver.browsers}";
          PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";
          PLAYWRIGHT_DRIVER_VERSION = pkgs.playwright-driver.version;
        });
      };
    };
}
