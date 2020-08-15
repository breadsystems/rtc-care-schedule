;; Internationalization.
;; TODO The design of this still needs to be fleshed out.
(ns rtc.i18n.core)


(defn t
  "Translate a phrase into the given lang. Fetches from the db or something. I dunno."
  [{:keys [lang i18n]} phrase-key]
  (get-in i18n [lang phrase-key]))

(defn i18n->lang-options
  "Takes a map of i18n data keyed by lang (typically a keyword like :en-US)
   and returns a vector of maps of the form {:value :en-US :label \"English\"}"
  [i18n]
  (map (fn [{:keys [lang lang-name]}]
         {:value lang :label lang-name})
       (vals i18n)))

(defn supported-langs [i18n]
  (set (map name (keys i18n))))

(defn supported? [lang i18n]
  (contains? (supported-langs i18n) (name lang)))