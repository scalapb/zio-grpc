let
  source = builtins.fromJSON (builtins.readFile ./nixpkgs.json);
in
import (builtins.fetchTarball {
  url = "https://github.com/NixOS/nixpkgs/archive/${source.rev}.tar.gz";
  inherit (source) sha256;
}) { }
