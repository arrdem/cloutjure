(ns cloutjure.processing.irc-links
  (:require [cloutjure.common :as common]
            [clojure.string :as string]
            [clojure.java.io :refer [reader]]
            [clojure.data.json :as json]
            [somnium.congomongo :as m])
  (:import [java.net URL]
           [org.apache.commons.validator.routines UrlValidator])
  (:gen-class))


(defn ^String substring?
  "True if s contains the substring."
  [substring ^String s]
  (.contains s substring))


(def tld-list
  (map (fn [l] (str "." (string/lower-case l)))
       (line-seq (reader "resources/tlds.txt"))))


(defn url?
  [link-maybe]
  (some #(substring? %1 link-maybe) tld-list))


(defn ensure-http
  [link-string]
  (if (substring? "http" link-string)
    link-string
    (str "http://" link-string)))


(defn ensure-trailing-slash
  [link-string]
  (if (= (last link-string) \/)
    link-string
      (str link-string "/")))


(defn normalize-url
  [link-string]
  (-> link-string
      (ensure-http)
      (ensure-trailing-slash)))


(defn date-map->rfc [message]
  (let [date (:date message)]
    ; 1985-04-12T23:20:50.52Z
    (format "%04d-%02d-%02dT%02d:%02d:%02d.00Z"
            (:years date)
            (:months date)
            (:days date)
            (:hours date)
            (:minutes date)
            (:seconds date))))


(def url->node (atom {}))

(defn ensure-node! [key value table ref]
  (if (contains? @ref key)
    (get @ref key)
    (let [node (m/insert! table value)]
      (swap! ref assoc key node)
      node)))         

(def message-count (atom 1))
(def thread-count (atom 0))


(defn process-author
  [author-name]
  (let [author-node (m/insert! :people {:#clojure {:nick author-name 
                                                   :posts (m/fetch-count :messages
                                                           :where {:author author-name})}})
        mongo-messages (m/fetch :messages 
                                :where {:author author-name})
        local-message-count (atom 0)]

    (doseq [m mongo-messages]
      (swap! local-message-count inc)
      (let [{:keys [message]} m
            words (string/split message #"[ \t]")]
        (doseq [w words]
          (when (url? w)
            (try
              (let [url (URL. (normalize-url w))
                    url-node  (ensure-node! w {:url w
                                               :site (.getHost url)
                                               :path (.getFile url)}
                                            :links
                                            url->node)]
                (m/insert! :link-refs {:author (:_id author-node)
                                       :date   (:date m)
                                       :url    (:_id url-node)}))
              (catch Exception e nil))))))

    (swap! message-count + @local-message-count)
    (print (format "\r[finished author] %s: %-10d messages!\n" 
                   author-name @local-message-count)))
  (swap! thread-count dec))


(defn print-progress-bar [percent]
  (let [bar (StringBuilder. "[")] 
    (doseq [i (range 50)]
      (cond (< i (int (/ percent 2))) (.append bar "=")
            (= i (int (/ percent 2))) (.append bar ">")
            :else (.append bar " ")))
    (.append bar (format "]  %-6f%%  threads: %d   " percent @thread-count))
    (print "\r" (.toString bar))
    (flush)))


(defn progress-bar-printer []
  (let [total-count (double (m/fetch-count :messages))]
    (loop []
      (print-progress-bar 
       (* (/ (double @message-count) total-count) 100))
      (Thread/sleep 100)
      (if-not (= @message-count total-count) 
        (recur)))))


(defn -main
  []
  (common/init-db!)

  (.start (Thread. progress-bar-printer))

  (let [authors (json/read-str (slurp "resources/authors.json"))
        target-thread-count 16]
    ;; set the thread count to the number to be spawned
    (reset! thread-count 0)
    ;; block while there are worker threads left
    (loop [remaining authors]
      (cond (empty? remaining)
              nil

            (> target-thread-count @thread-count)
              (let [chosen (first remaining)
                    remaining (rest remaining)]

                (print (format "\r[starting author] %s\n" chosen))
                (flush)

                (.start (Thread. (fn [] (process-author chosen))))
                (swap! thread-count inc)
                (recur remaining))

            (<= target-thread-count @thread-count)
              (do (Thread/sleep 1000)
                  (recur remaining))))))

