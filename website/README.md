# Website

This website is built using [Docusaurus 3](https://docusaurus.io/), a modern static website generator. It also uses [mdoc](https://scalameta.org/mdoc/) to compile and run the code in the markdown to ensure correctness.

Use Node 24 (see `../.node-version`) and Yarn 1.22.22.

### Installation

```
$ yarn install --frozen-lockfile
```

### Local Development

On one terminal, we are going to have `mdoc` watch our markdown files and process them whenever they change. Start `sbt`, and type the following command:

```
docs/mdoc --watch
```

On another terminal, go to the `website` directory, and type:

```
$ yarn start
```

This command starts a local development server and open up a browser window. Most changes are reflected live without having to restart the server.

### Build

```
$ yarn build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

### Deployment

The website is deployed through Github actions after all CI checks pass for a commit pushed to master. Artifact publishing and documentation deployment are independent jobs.

### Dependency maintenance

The `resolutions` in `package.json` select patched versions of transitive build
packages that upstream dependencies still constrain to older majors. Production
builds and desktop/mobile browser checks validate these overrides. Revisit them
when upgrading Docusaurus. The remaining `braces` advisory (GHSA-vfj7-8cjw-p6xm)
has no patched release at the time of this update; it is used by development/build
file matching, not shipped as a server in the static website.
