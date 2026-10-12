{ pkgs ? import ./nix/pkgs.nix }:
pkgs.mkShell {
  packages = with pkgs; [
    (sbt.override { jre = jdk21; })
    jdk21
    nodejs_24
    yarn
    git
    bashInteractive
  ];

  # The protoc binary downloaded by sbt-protoc needs libstdc++ on NixOS.
  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc.lib ];
}
