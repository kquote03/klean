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
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ sdk pkgs.jdk21 pkgs.git pkgs.curl pkgs.sqlite ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk21.home;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildTools}/aapt2";
      };
    };
}
