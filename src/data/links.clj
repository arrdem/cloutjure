(ns cloutjure.data.links
  (:require [somnium.congomongo :as m]))

;; ## Links
;;
;; Links are maps
;;
;; ```Clojure
;; {
;;  :url    url,
;;  :refc   counter,
;;  :author author
;; }
;; ```
;;
;;  - `author` - a string, being the handle or real name of the author of the linked content
;;  - `refc` - an integer which is incremented every time the link is referred to
;;  - `url` - the link itself, used to de-duplicate
;;
;; Links are stored in their own link table, and are accessed primarily
;; by the `url` field. The desired api is essentially `(url/inc! <url>)`,
;; where URLs which are not yet populated with an `author` get pushed
;; into a queue for human review. However `(url/maybe-add! <url>)` or
;; something equivalent should work too.


(def ^:dynamic *collection* :links)

(defn link? 
  "Checks the argument object agains the link datastructure
  specification, returning True if the object matches, else False."

  [maybe-link]
  (and (map? maybe-link)
       (every? (partial contains? maybe-link) 
               [:url :refc :author])
       (string? (:url maybe-link))
       (string? (:author maybe-link))
       (number? (:refc maybe-link))))


(defn fetch
  "Fetches a link from the link table. The behaviour in the case of
  attempting to fetch a non-existant link record is left to the
  database driver."

  [link-or-url]
  (cond (link? link-or-url)
          (m/fetch *collection* (select-keys [:url] link-or-url))
        (string? link-or-url)
          (m/fetch *collection* {:url link-or-url})))


(defn del!
  "Deletes a link from the link table."

  [link-or-url]
  (if-let [entry (get link-or-url)]
    (m/destroy! *collection* entry)))


(defn inc! 
  "Looks up a link from the link table (creating an entry if one is
  not found) and increments the link's refcount. Intended to be used
  for counting the number of times some link has been ref'd on a mail
  list or other community."

  [link-or-url] 
  (cond (link? link-or-url)
          (m/update! *collection* link-or-url {:$inc {:refc 1}})
        (string? link-or-url)
          (if-let [entry (get link-or-url)]
            (m/update! *collection* entry {:$inc {:refc 1}})
            (add-link! link-or-url))))

