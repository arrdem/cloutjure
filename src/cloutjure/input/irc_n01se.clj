(ns cloutjure.input.irc_n01se
  (:require [cloutjure.data
             [hashes   :refer [sha-256]]]

            [cloutjure.parallelism :refer [work]]

            [pl.danieljanus.tagsoup
             :as tagsoup]

            [clj-time
             [core     :as t]
             [format   :as t.f]]

            [bitemyapp.revise
             [connection :refer [connect close]]
             [query    :as r]
             [core     :refer [run run-async]]]

            [clojure
             [stacktrace :refer [root-cause]]
             [string     :refer [trim]]])
  (:gen-class))


(def url-formatter (t.f/formatter "YYYY-MM-dd"))
(def fmt (partial format "%20.20s | %20.20s | %80.80s"))

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
          uname (trim uname)]
      (when (and (= _b :b)
                 (not (= uname "*")))
        (let [user (second (re-find #"(\S*?):.*" uname))]
          (reset! name-atom user)))))

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

  [conn {:keys [db table]} day]
  (try
    (let [tree (tagsoup/parse (date->url day))
          name-atom (atom "")]
      (doseq [messages (as-> tree v
                            (get-in v[3 3 8])
                            (drop 2 v)
                            (filter #(= :p (first %1)) v))]
        (let [message (p->message name-atom day messages)]
          (try
            (assert (not (= (:author message) " ")))
            (-> (r/db db)
                (r/table-db table)
                (r/insert message)
                (run conn)
                :error
                not
                (assert "Failed to write back message!"))

            (catch Exception e
              (println (fmt day e (pr-str message)))))))

      (println (fmt day "End of day" (str "last message from " @name-atom))))

    (catch Exception e
      (println (fmt day e (root-cause e))))))


(defn ->worker [clj-epoch conn conn-opts]
  (fn [offset]
    (let [date (->> offset
                    (t/days)
                    (t/plus clj-epoch))]
         (process-days-log conn conn-opts date)
         (spit "snapshot.log"
               (t.f/unparse url-formatter date)))))


(defn -main
  "The entry point of this codebase, essentially a simple crawler
  script which browses http://clojure-log.n01se.net/date/* and uses
  tagsoup to parse the html into a series of messages which are loaded
  into the cloutjure datastore via cloutjure.data as one would expect.

  This function takes no options and is desinged to be run as a single
  threaded script with no paralellism besides any which Clojure may
  sneak in behind my back."

  [cfgfile]
  (let [{:keys [conn-opts worker-count snapshotfile]}
                  (read-string (slurp cfgfile))
        {:keys [db table clean]} conn-opts
        conn      (connect conn-opts)
        clj-epoch (t.f/parse url-formatter
                             (slurp snapshotfile))]

    (println clj-epoch)

    (when clean
      (do (println "[DEBUG] Clobbering existing DB in 10s...")
          (Thread/sleep 10000)
          ;; clobber the existing table if one exists...
          (-> (r/db db)
              (r/table-drop-db table)
              (run conn))

          ;; create a new empty database
          (-> (r/db db)
              (r/table-create-db table)
              (run conn))))

    (let [work-range (->> (t/interval clj-epoch
                                      (t/now))
                          t/in-days
                          range)
          fns   (->> (range worker-count)
                     (map (fn [_]
                            (->worker clj-epoch
                                      (connect conn-opts)
                                      conn-opts))))
          workers (map (fn [x y] #(x y))
                       (cycle fns)
                       work-range)]
      (work workers worker-count))))
