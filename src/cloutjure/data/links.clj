(ns cloutjure.data.links)

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
