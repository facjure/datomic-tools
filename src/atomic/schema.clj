(ns atomic.schema
  (:refer-clojure :exclude [name])
  (:require [environ.core :refer [env]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [datomic.api :as d]
            [clojure.tools.logging :as log]
            [atomic.db :as db]
            [atomic.utils :refer :all])
  (:import datomic.Util))

(def allowed-types
  "Allowed values for Schema definitions. Maps to types in Dataomic
  http://docs.datomic.com/schema.html#sec-1"
  (sorted-set :keyword :instant :uuid :boolean :bytes :string
              :bigint :long :float :double :bigdec :uri :ref))

(defn valid-type?
  "Check and throw an error if an invalid type is given for :db/valueType."
  [type]
  (if (contains? allowed-types type)
    type
    (throw (ex-info (str type " is not a valid attribute type.\n"
                         " Allowed types: " allowed-types) {}))))

(defn load-edn [conn fname]
  "Load Edn schema from resources"
  (doseq [txd (Util/readAll (io/reader (io/resource fname)))]
    (d/transact conn txd)))

(defn has-attribute?
  "Does database have an attribute named attr-name?"
  [conn attr-name]
  (-> (d/entity (d/db conn) attr-name)
      :db.install/_attribute
      boolean))

(defn create-attribute [conn schema]
  "Create a schema from an attribute definition vector:
   Example:
       create-attribute([:story/title 'full title' :string :one :fulltext :index])"
  (let [stringify #(subs (str %) 1)
        attr (nth schema 0)
        sch (conj {} {:db/id (d/tempid :db.part/db)})
        sch (conj sch {:db/ident attr})
        sch (conj sch {:db/doc (nth schema 1)})
        sch (conj sch {:db/valueType (let [vtype-kwd (nth schema 2)
                                           vtype (stringify vtype-kwd)]
                                       (keyword "db.type" vtype))})
        sch (conj sch {:db/cardinality (let [cardi-kwd (nth schema 3)
                                             cardi (stringify cardi-kwd)]
                                         (keyword "db.cardinality" cardi))})
        optional-attr? (> (count schema) 4)

        sch (if optional-attr?
              (cond
                (some #{:fulltext} (nthrest schema 4)) (conj sch {:db/fulltext true})
                (some #{:component} (nthrest schema 4)) (conj sch {:db/isComponent true})
                (some #{:index} (nthrest schema 4)) (conj sch {:db/index true})
                (some #{:unique-value} (nthrest schema 4))  (conj sch {:db/unique :db.unique/value})
                (some #{:unique-identity} (nthrest schema 4)) (conj sch {:db/unique :db.unique/identity})
                (some #{:no-history} (nthrest schema 4)) (conj sch {:db/noHistory true}))
              sch)

        sch (conj sch {:db.install/_attribute :db.part/db})]
    (when-not (has-attribute? conn attr)
      (d/transact conn (vector sch)))))

(defn create-attributes [conn schema]
  "Create a schema from multiple attribute definition vectors"
  (map (create-attribute conn) schema))

(defn find-attribute [conn attr]
  (d/q '[:find ?attr
         :in $ ?name
         :where [?attr :db/ident ?name]]
       (d/db conn)
       conn
       attr))

(defn has-schema? [conn schema-attr schema-name]
  "Does database have a schema-name installed? Uses schema-attr
   (an attr of transactions) to track which schema names are installed."
  (and (has-attribute? conn schema-attr)
       (-> (d/q '[:find ?e
                  :in $ ?sa ?sn
                  :where [?e ?sa ?sn]]
                (d/db conn) schema-attr schema-name)
           seq boolean)))

(defn find-cardinality
  "Returns the cardinality :db.cardinality/one or :db.cardinality/many)"
  [conn attr]
  (->>
   (d/q '[:find ?v
          :in $ ?attr
          :where
          [?attr :db/cardinality ?card]
          [?card :db/ident ?v]]
        (d/db conn) attr)
   ffirst))
