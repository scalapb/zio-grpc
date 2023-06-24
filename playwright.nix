{pkgs ? import <nixpkgs> {
  config = {
    packageOverrides = pkgs: {
      sbt = pkgs.sbt.override { jre = pkgs.openjdk11; };
    };
  };
}} :
(pkgs.buildFHSUserEnv {
  name = "zio-grpc";
  targetPkgs = pkgs: [
    pkgs.sbt
    pkgs.openjdk11
    pkgs.nodejs
    pkgs.yarn
    pkgs.playwright

    # keep this line if you use bash
    pkgs.bashInteractive
  ];

  runScript = "bash";

  profile = "export PLAYWRIGHT_BROWSERS_PATH=${pkgs.playwright-driver.browsers}";
}).env
