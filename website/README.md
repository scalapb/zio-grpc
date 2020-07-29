# Website

This website is built using [Docusaurus 2](https://v2.docusaurus.io/), a modern static website generator. It also uses [mdoc](https://scalameta.org/mdoc/) to compile and run the code in the markdown to ensure correctness.

### Installation

```
$ yarn
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

The website is deployed through Github actions whenever a commit is pushed to master.