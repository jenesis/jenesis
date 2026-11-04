(ns sample.welcome
  (:import [sample Greeter])
  (:gen-class))

(defn welcome []
  (str (.hello (Greeter.)) " from Clojure, compiled by Jenesis!"))

(defn -main [& _]
  (println (welcome)))
