(ns clojars-stats-fetcher.core
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [clojure.data.json :as json]))

(defn download-clj-depedencies
  "This function downloads a list of all packages as a clj dependencies formatted file."
  []
  (let [{:keys [body] :as resp} @(http/get "https://clojars.org/repo/all-jars.clj")]
    body))

(defn deps-to-pkgs-lst
  "This function grabs a raw clj deps list in raw format and parses it, removing
  duplicates and returning a list of unique packages."
  [deps]
  (distinct (map #((re-matches #"^\[([a-zA-Z0-9/.\-_]+) .*$" %) 1)
                 (str/split deps #"\n"))))

(defn mk-fetch-pkg-data
  "This function returns a function that downloads a given package's metadata
  and returns it in plaintext"
  [print-each num-pkgs]
  (fn fetch-pkg-data
    [i pkg]
    (when (= (rem i print-each) 0)
        (println "Downloaded data for" i "/" num-pkgs))
    (let [uri (str "https://clojars.org/api/artifacts/" (str/lower-case pkg))
         {:keys [body error]} @(http/get uri)]
         (if error
             (fetch-pkg-data i pkg)
             body))
    ))

(defn fix-csv-special-chars
  "This function escapes special characters (\"\\n) from strings to be included
  in CSV files."
  [str]
  (if (not (nil? str))
      (clojure.string/replace
        (clojure.string/replace str #"(\")" "\"\"")
        #"\n"
        "\\\\n")))


(defn json-to-csv
  "This function converts a package's metadata in json to a csv entry."
  [j]
  (str "\"" (if (= (j "jar_name") (j "group_name"))
                (j "jar_name")
                (str (j "group_name") "/" (j "jar_name"))) "\""
       ","
       "\"" (j "homepage") "\""
       ","
       "\"" (fix-csv-special-chars (j "description")) "\""
       ","
       (j "downloads")
       "\n"))

(defn -main [& args]
  (let [filename (if (nil? (first args)) "out.csv" (first args))
        package-list (deps-to-pkgs-lst (download-clj-depedencies))]

    (spit filename "name,homepage,description,downloads\n")
    (println "Downloaded list, processing" (count package-list) "elements.")

    (let [responses (map-indexed (mk-fetch-pkg-data 100 (count package-list))
                                 package-list)]

         (doall (for [r responses]
                     (let [j (if (not= r "")
                                 (json/read-str r)
                                 false)]
                          (if j
                              (spit filename (json-to-csv j) :append true))))))))
