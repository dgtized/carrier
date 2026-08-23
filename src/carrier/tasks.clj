(ns carrier.tasks
  (:require [babashka.fs :as fs]
            [babashka.tasks :as bt]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [carrier.revision :as cr]))

(defn clean [& dirs]
  (doseq [dir dirs]
    (println (format "[carrier] Cleaning %s" dir))
    (fs/delete-tree dir)))

(defn revision-span
  "Generate a version span with time of build and git commit used to build."
  [timestamp sha]
  (str "<span id=\"revision\" title=\""
       timestamp
       "\"><code>rev:"
       (subs sha 0 8)
       "</code></span>"))

;; TODO: use variables from figwheel config?
(defn default-opts
  [{:keys [build-dir manifest release index-file] :as opts}]
  (assoc opts
         :build-dir (or build-dir "target/public/cljs-out/")
         :manifest (or manifest "manifest.edn")
         :release (or release "release-main.js")
         :index-file (or index-file "index.html")))

(defn release-file
  "Calculate the release name including the uniqueness slug.

  It reads the figwheel manifest to translate from a release like
  \"target/public/cljs-out/release-main.js\" to
  \"target/public/cljs-out/release-main-SHA.js\", so that the correct filename
  can be inserted in the index file. "
  [opts]
  (let [{:keys [build-dir manifest release]} (default-opts opts)
        manifest-file (str build-dir manifest)
        release-build (str build-dir release)
        manifest-db (edn/read-string (slurp manifest-file))]
    (fs/file-name (get manifest-db release-build))))

(defn build-static-site
  "Build a directory in `to` containing js release, index file and any other sources in
  the `from` directory."
  [& opts]
  (let [{:keys [build-dir from to release]} (default-opts opts)
        js-dir (str to "/js")
        release-base (fs/file-name (fs/strip-ext release))
        release-glob (str "/" release-base "*")]
    (fs/delete-tree to)
    (println "[carrier] Creating" to "from" from "with javascript")
    (fs/copy-tree from to)
    (fs/create-dirs js-dir)
    (doseq [js (fs/glob build-dir (str "**" release-base "*"))]
      (fs/copy js js-dir))
    (bt/shell "bash" "-c" (str "ls -hs --format=single-column " js-dir release-glob))))

(defn rewrite-index
  "Rewrite the `index.html` for release.

  By default Figwheel references a dev release for each build, for publishing
  remotely, rewrite the `release-file` to include the released SHA slug so that
  it invalidates cache on load.

  If `base-href` appears in the index, replace with the value in `base-href`.
  Helps for correctly setting a base URL if all of the navigation is using
  push_state to adjust the URL on the client.

  Finally, replaces a span with id revision with a current date/time of release
  and last git commit of the release."
  [& opts]
  (let [{:keys [index-file from to base-href] :as opts} (default-opts opts)
        from-index (str (fs/path from index-file))
        to-index (str (fs/path to index-file))
        revision (cr/git-revision)
        timestamp (cr/timestamp-iso8601)
        contents (slurp from-index)]
    (println "[carrier] Rewriting" from-index "->" to-index)
    (println "  build:" timestamp "rev:" revision)
    (spit to-index
          (-> contents
              (str/replace-first #"<base href=\"\">"
                                 (str "<base href=\"" base-href "\">"))
              (str/replace-first #"cljs-out\/dev-main\.js"
                                 (str "js/" (release-file opts)))
              (str/replace-first "<span id=\"revision\"><code>rev:abcdef12</code></span>"
                                 (revision-span timestamp revision))))))
