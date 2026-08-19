(ns carrier.revision
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.string :as str])
  (:import java.time.Instant
           java.time.format.DateTimeFormatter))

(defn timestamp-iso8601 []
  (let [fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")]
    (.format fmt (.atZone (Instant/now) (java.time.ZoneId/of "Z")))))

(defn git-revision
  "Current revision SHA for the git HEAD"
  []
  (-> (sh "git" "rev-parse" "HEAD") :out str/trim))
