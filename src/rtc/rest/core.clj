(ns rtc.rest.core
  (:require
   [clojure.data.json :as json]
   [cognitect.transit :as transit]
   [rtc.auth.core :as auth]
   [rtc.appointments.core :as appt])
  (:import
    [java.io ByteArrayOutputStream]
    [java.util Date]
    [java.text SimpleDateFormat]))


(def ^:private default-uid
  (when (= "1" (System/getenv "DEV_DISABLE_AUTH")) 1))

(defn- req->uid [req]
  (get-in req [:session :identity :id] default-uid))

(defn- ->transit [body]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer body)
    (.toString out)))


(defn- ->json-value [_ v]
  (cond
    (= Date (type v)) (let [format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")]
                        (.format format v))
    :else v))

(defn- ->json [x]
  (json/write-str x :value-fn ->json-value))

(defn- rest-handler [f]
  (fn [req]
    (let [;; The frontend always consumes application/transit+edn data,
          ;; but application/json is useful for debugging
          json? (boolean (get (:params req) "json"))
          transform (if json? ->json ->transit)
          content-type (if json? "application/json" "application/transit+edn")
          res (f req)]
      (if (:success res)
        {:status  200
         :headers {"Content-Type" content-type}
         :body    (transform {:success true
                              :data    (:data res)})}
        {:status 400
         :headers {"Content-Type" content-type}
         :body    (transform {:success false
                              :errors  (:errors req)})}))))

(defonce ONE-DAY-MS (* 24 60 60 1000))

(defn endpoints [{:keys [mount]}]
  [mount
   ["/windows"
    {:get (rest-handler (fn [{:keys [params]}]
                          (let [;; TODO snap to midnight this morning
                                now (inst-ms (Date.))
                                midnight-this-morning now
                                ;; TODO tighten up this logic for more accurate availability
                                ;; Look for availabilities starting this time five days from now
                                from (+ midnight-this-morning (* 5 ONE-DAY-MS))
                                to (+ from (* 28 ONE-DAY-MS))
                                state (get params "state")]
                            {:success true
                             :data    (appt/appt-req->windows {:from from
                                                               :to to
                                                               :state state})})))}]
   ["/appointment"
    {:post (fn [req]
             {:status 200
              :headers {"Content-Type" "application/transit+edn"}
              :body (->transit {:success true
                                :appointment (:params req)})})}]])

(comment

  ;;
  )