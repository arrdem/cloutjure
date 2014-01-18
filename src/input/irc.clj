(ns cloutjure.input.irc
  (:require (cloutjure.data [hashes   :refer [sha-256]])
            [pl.danieljanus.tagsoup   :as tagsoup]
            [clj-time       [core     :as t]
                            [format   :as t.f]]
            [bitemyapp.revise  
                            [connection :refer [connect close]]
                            [query    :as r]
                            [core     :refer [run-async]]])
  (:gen-class))


(def url-formatter (t.f/formatter "YYYY-MM-dd"))
(def fmt (partial format "%20.20s | %20.20s | %40.40s"))

(defn date->url
  "Formats a clj-time date into a logfile path for
  http://clojure-log.n01se.net/date/*."
  [date]
  (format "http://clojure-log.n01se.net/date/%s.html"
          (t.f/unparse url-formatter date)))


(def hour-minute-formatter (t.f/formatters :hour-minute))

(defn p->message
  "Converts a [:p {} [:a {} <date-in-minutes-and-seconds>] 
                     [:b {} <user>]
                     & message]

   into the appropriate messages/message? compliant
   datastructure. Note that this implementation _does_ preserve the
   exact message text, which must be explicitly stripped before it is
   inserted to the database never to be destroyed."
  [name-atom year-month-day-date [_p _m0 [_a _m1 date] & tail]]

  (if (vector? (first tail))
    (let [[_b _m uname] (first tail)
          user (second (re-find #"(\w*?):.*" uname))]
      (when (or (not (re-find #"\\*" uname))
                user)
        (reset! name-atom user))))
      
  (let [message (if (vector? (first tail)) (rest tail) tail)
        message (apply str (interpose " " message))]
    {:message message
     :author  @name-atom
     :date    (let [{:keys [hours minutes]} 
                    (->> date
                         (t.f/parse hour-minute-formatter)
                         (t.f/instant->map))]
                (t.f/instant->map
                 (t/plus year-month-day-date
                         (t/hours   hours)
                         (t/minutes minutes))))
     :hash    (sha-256 message)}))


(defn process-days-log
  "Takes a date and attempts to parse the day's logs, writing to the
  datastore as appropriate."

  [conn day]
  (try
    (let [tree (tagsoup/parse (date->url day))
          name-atom (atom "")]
      (doseq [message (as-> tree v
                           (get-in v[3 3 8])
                           (drop 2 v)
                           (filter #(= :p (first %1)) v))]
        (let [message (p->message name-atom day message)]
          (try
            (assert (not (= (:author message) " ")))
            (assert 
             (not 
              (:error
               (-> (r/db "cloutjure")
                   (r/table-db "n01se")
                   (r/insert message)
                   (run-async conn)))))
            (catch Exception e 
              (println (fmt day e (pr-str message))))))))

    (println (fmt day "End of day" nil))

    (catch Exception e 
      (println (fmt day e nil)))))


(defn -main
  "The entry point of this codebase, essentially a simple crawler
  script which browses http://clojure-log.n01se.net/date/* and uses
  tagsoup to parse the html into a series of messages which are loaded
  into the cloutjure datastore via cloutjure.data as one would expect.

  This function takes no options and is desinged to be run as a single
  threaded script with no paralellism besides any which Clojure may
  sneak in behind my back."

  []
  (let [conn      (connect {:host "brick"
                            :port 28015})
        clj-epoch (t/date-time 2008 2 1) ;; first logged day
        interval  (t/interval clj-epoch (t/now))]
    (doseq [chunks (->> interval
                        t/in-days
                        range
                        (partition 8))]
      (->> chunks
           (mapv (fn [offset]
                  (future
                    (->> offset
                         (t/days)
                         (t/plus clj-epoch)
                         (process-days-log conn)))))
           (mapv deref))
      (println "Done with chunks:" chunks))))

