(ns rtc.auth.core
  (:require
   [buddy.auth :refer [authenticated? throw-unauthorized]]
   [buddy.auth.backends.session :refer [session-backend]]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [config.core :as config :refer [env]]
   [rtc.auth.two-factor :as two-factor]
   [rtc.users.core :as u]
   [rtc.auth.util]
   [rtc.db :as db]
   [rtc.layout :as layout]
   [ring.util.response :refer [redirect]]))


(defn- auth-disabled? []
  (boolean (:dev-disable-auth env)))

(comment
  (u/email->user "rtc@example.com")
  (u/admin? (u/email->user "rtc@example.com"))
  (u/preferences (u/email->user "rtc@example.com"))
  (u/two-factor-enabled? (u/email->user "rtc@example.com"))
  (u/authenticate "rtc-admin@example.com" "[PASSWORD HERE]")
  (u/authenticate "rtc-admin@example.com" "garbage"))


(defn login-step [{:keys [params] :as req}]
  (cond
    (and
     (authenticated? req)
     (two-factor/verified? req))
    :logged-in

    (and (authenticated? req) (:token params))
    :verifying

    (authenticated? req)
    :two-factor

    (and (:email params) (:password params))
    :authenticating

    :else
    :unauthenticated))

(defn destination-uri [{:keys [query-params]}]
  (let [dest (get query-params "next")]
    (if (seq dest) dest "/comrades")))

(defn logout-handler [_req]
  (-> (redirect "/login")
      (assoc :session {})))

(defn login-handler [{:keys [params session] :as req}]
  (prn params)
  (condp = (login-step req)
    :unauthenticated
    (layout/login-page {:req req})

    :authenticating
    (if-let [user (u/authenticate (:email params) (:password params))]
      (-> (layout/two-factor-page req)
          ;; Recreate the session due to privilege escalation.
          (assoc :session (vary-meta (:session req) assoc :recreate true))
          ;; Persist our user identity in the session.
          (assoc-in [:session :identity] user))
      (layout/login-page {:req req
                          :error "Invalid email or password"}))

    :two-factor
    (layout/two-factor-page req)

    :verifying
    (if (two-factor/verify-token (:token params) 25490095)
      (-> (redirect (destination-uri req))
          (assoc :session (assoc session :verified-2fa-token? true)))
      (layout/two-factor-page {:req req
                               :error "Invalid token"}))

    :logged-in
    (redirect (destination-uri req))))

(defn admin-only-resolver [resolver]
  (fn [{:keys [request] :as context} query-string value]
    (if (or (:auth-disabled? (:env request))
            (u/admin? (:identity (:session request))))
      (resolver context query-string value)
      {:errors [{:message "You do not have permission to do that"}]})))

(defn wrap-identity
  "Persist session identity directly into request"
  [handler]
  (fn [{:keys [session] :as req}]
    (handler (-> req
                 (assoc :identity (:identity session))
                 (assoc-in [:env :auth-disabled?] (auth-disabled?))))))

(defn wrap-require-auth [handler]
  (fn [req]
    (if (or
         (and
          (authenticated? req)
          (two-factor/verified? req))
         ;; Support disabling auth for nicer frontend dev workflow
         (auth-disabled?))
      (handler req)
      (throw-unauthorized))))

(def auth-backend
  (session-backend
   {:unauthorized-handler (fn [{:keys [uri]} _metadata]
                            (redirect (format "/login?next=%s" uri)))}))


(defn wrap-auth [handler]
  (-> handler
      (wrap-require-auth)
      (wrap-authorization auth-backend)
      (wrap-authentication auth-backend)))