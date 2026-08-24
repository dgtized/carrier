# Carrier

Resusable Babashka scripts for publishing Figwheel based Clojurescript projects to Github Pages. The name carrier is a reference to a common carrier for transportation.

The basic idea is that a basic `bb.edn` like below can clean, compile and construct a publishable artifact for a github page in `static-site` for upload using a simple github workflow with `actions/deploy-pages`.

```
{:paths ["src"]
 :deps
 {io.github.dgtized/carrier
  {:git/url "https://github.com/dgtized/carrier.git" :git/sha "LATEST"}}
 :tasks
 {:requires ([babashka.fs :as fs]
             [carrier.tasks :as t])
  clean
  {:task (t/clean "target" "static-site")}
  compile
  {:depends [clean]
   :doc "Compile release with figwheel"
   :task (shell "clojure -Mfig -m figwheel.main -bo release")}

  create-static-site
  {:task (t/build-static-site
          :from "resources/public"
          :to "static-site")}

  rewrite-index
  {:task (t/rewrite-index
          :from "resources/public"
          :to "static-site"
          :base-href "https://user.github.io/PROJECT/")}

  build
  {:doc "Build artifact for redistribution"
   :depends [compile create-static-site rewrite-index]}

  view
  {:task (shell "xdg-open static-site/index.html")}}}

```

Example workflow for a `.github/workflows/continuous-deployment.yaml`:

```
name: Continuous Deployment

on:
  push:
  pull_request:
  workflow_dispatch:

concurrency:
  group: "pages"
  cancel-in-progress: true

jobs:
  build:
    name: Build
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest

    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v7
        with:
          filter: tree:0

      - name: Cache deps.edn dependencies
        uses: actions/cache@v6
        with:
          path: |
            ~/.m2
            ~/.gitlibs
          key: clj-${{ runner.os }}-${{ hashFiles('**/deps.edn') }}
          restore-keys: clj-${{ runner.os }}-

      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25

      - name: Setup Clojure
        uses: DeLaGuardo/setup-clojure@13.6.1
        with:
          cli: latest
          bb: latest

      - name: Build
        run: bb build

      - name: Upload artifact
        id: deployment
        uses: actions/upload-pages-artifact@v5
        with:
          path: 'static-site'

  publish:
    name: Publish
    needs: build
    runs-on: ubuntu-latest

    permissions:
      pages: write
      id-token: write

    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}

    steps:
      - name: Configure Pages
        uses: actions/configure-pages@v6

      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v5
```

## Rewrite Index

If the `index.html` contains a span like:
```
<span id="revision"><code>rev:abcdef12</code></span>

```

Then the index file will be rewritten with a build time and git revision used to create the artifact.

If `<base href=""\>` shows up then the `base-href` specified in the call to `rewrite-index` in the `bb.edn` file will replace that url.

`cljs-out/dev-main.js` will be rewritten to reference `release-main-$SHA.js` to ensure that a the source javascript incorporates a cache busting key so that it loads the new code on next page load.
