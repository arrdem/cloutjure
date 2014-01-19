(ns cloutjure.parallelism
  (:import (java.util.concurrent
            Executors)))

(defn- work*
  [fns threads]
  (let [pool (Executors/newFixedThreadPool threads)]
    (.invokeAll pool fns)))

(defn work
  "takes a seq of fns executes them in parallel on n threads, blocking
  until all work is done."  
  [fns threads]
  (map #(.get %) (work* fns threads)))

(defn map-work
  "like clojure's map or pmap, but takes a number of threads, executes
  eagerly, and blocks."  
  [f xs threads]
  (work (map (fn [x] #(f x)) xs) threads))
