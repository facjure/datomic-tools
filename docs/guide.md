---
author: Priyatam Mudivarti
title: A rough guide to Datomic
license: [CC by NC 3.0](http://creativecommons.org/licenses/by-nc/2.0/)
tags:
  - datomic
  - distributed-databases
  - functional-databases
  - clojure
  - datalog
---

# Introduction

> Store a collection of facts
  that do not change, add
  new facts like memory
  superseding old facts.
  Build a snapshot of facts
  that collect knowledge
  over time and reveal meaning

Datomic considers a database to be an information system, where information is a set of facts, and facts are things that have happened. In effect, Datomic a [CP](http://en.wikipedia.org/wiki/CAP_theorem) system that stores a collection of Facts, as events, and each event describes what happened at what _time_, using five attributes. Once stored in memory facts do not change. 

Naturally, Datomic supports only two operations: _add_ and _retract_.

Old facts can be superseded by new facts over time and the state of the database is a value defined by the set of facts _in effect_ at a given moment in time. Hence, Datomic’s data model—based on immutable facts stored over time, enables a physical design that is different from most databases. 

Instead of processing all requests in a single server component, Datomic distributes _read_, _write_ and _query processing_ across different components, and swaps storage engines. This separation is achieved by creating distinct apis for each with the notion of local and remote snapshots of data. Since data is always a snapshot in time (you always get the same value), you can ask questions like _what is the current policy?_ or _what happened to the user as of last year_ from a local repo. By appending facts over time, network calls are cut down to the most recent snapshot. By using a richer api for reads, directly from clojure and its data structures, abstractions like sql, map-reduce, or pig become redundant. By delegating the storage engine to any blob on engines like Couchbase, Dynamodb, Riak, or Postgres, Datomic further virtualizes of cloud storage providers. 

Essentially, Datomic makes NoSql solutions redundant.

# Getting Started

## Installation

Download the latest version of [Datomic Free](http://www.datomic.com/get-datomic.html):

    wget https://my.datomic.com/downloads/free/0.9.5067
    unzip datomic-free-0.9.5067.zip

For [Datomic Pro Starter](http://downloads.datomic.com/pro.html), sign for a free license key. Once you download the key, make sure you swap the dependencies in [project.clj](project.clj).

## Configuration

[Environ](https://github.com/weavejester/environ) is used to manage environment variables for AWS, Heroku, Cassandra and other storage engines.

Add the follwing keys in `~/.lein/profiles.clj`:

Setup the type in env:

    :datomic-dbtype = :free
    :datomic-name ("test")

Postgres (Heroku):

    :datomic-jdbc-url

AWS:

    :aws-access-key
    :aws-secret-key

Let's see if our setup works.

    lein deps (first time)
    lein repl

If all goes well, you should see a repl with no errors.

## Running Datomic

If you purchased a Pro-Starter license, copy the relevant config properties from `config/samples/<type>.properties` to `config/dev.properties`. Edit config/dev.properties and paste your license key.

Start the transactor (it runs datomic):

    cd <datomic-folder>
    bin/transactor config/samples/free-transactor-template.properties

# PART I - Concepts

Datomic is designed to be directly programmable using data that represents the domain model, represented as Entities with Attributes and Values. The primary interface to Datomic is data, not strings, not DDL or DML. 

An Entity is referred by a generated id or keyword. Attributes are simply namespaced-keywords. A value can be scalar (String, Integer, etc.,), or a reference to another Entity. This reference establishes a relationship between two entities. Though analogous to the concept of a _foreign-key_, Datomic reference is just the value of the referenced attribute.

Entities in Datomic are definied recursively, like lists in lisp.

## Facts

The data model in Datomic is based around atomic facts called datoms: a single, flat, universal relation. There is no other structural component to Datomic. 

A datom is a 4-tuple consisting of:

    [entity attribute value transaction]

Any model in real or manufactured world can be represented in this 4-tuple.

For ex, a Person can be created as:

    [100 :person/firstName "Rich" 1000]

Note that entity and transaction here are arbitrary numbers (can be generated by Datomic).

A blog can be modeled as two facts:

    [200 :blog/title "on datomic" 1000]
    [200 :blog/entry "datomic will change the way you think of databases" 1000]

A registration page can save a web form as a set of facts:

     [100 :user.registration/name "clojure-addict" 1000]
     [200 :user.registration/email "hello@example.com" 1000]
     [300 :user.registration/address "1 mission street san francisco ca 94103" 1000]

And so on.

## Entities, Attributes & Values

A Datomic entity is simply a _Fact_ providing a lazy, associative view of all the information that can be reached from its id.

Let' revisit our 4-tuple Fact:

    [entity attribute value transaction]

- entity: entity id (typically, an auto-generated id)
- attribute: Clojure keyword representing both the model (namespace :person) 
  and attribute name (firstName)
- value: any value
- transaction: tx id generated by datomic; used internally for time-based 
  queries

Note that entities are not a mapping layer between databases and application code: they're a direct translation from information stored in the database to application as raw data structures.

Entity references are bi-directional by default:

    [{:db/id #db/id[:db.part/user -1]
      :person/name "Bob"
      :person/spouse #db/id[:db.part/user -2]}
     {:db/id #db/id[:db.part/user -2]
      :person/name "Alice"
      :person/spouse #db/id[:db.part/user -1]}]

Entity attributes are accessed lazily as you request them. 

They aren't typed.

_Let's revisit_: Fact (datom) is entity-attribute-value-transaction. Therefore, attribute/value pair and the transaction are associated with the entity, which is a number. The transaction holds the time. We create a new entity when we transact an attribute without specifying entity. We change the attribute (add, "update") when we transact with the entity.

**On Indentity**:

Datomic auto-generates entity ids and stores them as part of every datom. To simplify application access, `:db/ident` provides a keyword-based lookup to entities that can be used by apis querying through entity 'id'.

For ex the following record can be accessed via `:person/name` lookup, instead of the generated id.

    {:db/id #db/id[:db.part/db]
     :db/ident :person/name}

Identities can also be used to implement enumerated tags.

    {:db/id #db/id[:db.part/db]
     :db/ident :label/type
     :db/doc "Enum, one of :label.type/distributor, :label.type/holding,
        :label.type/production, :label.type/originalProduction,
        :label.type/bootlegProduction, :label.type/reissueProduction, or
        :label.type/publisher."}

`Unique identities` allow attributes to have unique values.

    {:db/id #db/id[:db.part/db]
     :db/ident :person/email
     :db/unique :db.unique/identity}

A unique identity attribute is always indexed by value.

`Lookup Refs' are _Business Keys_ on steroids: a list containing an attribute and value.

    [:person/email "joe@example.com"]

To refer to existing entities in a transaction, avoiding extra lookup code, use:

    {:db/id [:person/email "rich@example.com"]
     :person/name :rich}

Note that Lookup refs _cannot_ be used in queries.

`Squuids` provide efficient, globally unique identifiers. They are _not_ real UUIDs, but they come close as semi-sequential uuids. As long you don't generate thousands of squuids every millisecond, indexes based on squuids will not fragment: the first part of a squuid is based on the system time.

`ident` is designed to be fast. 

In general, queries against a single database can lookup entity ids via other kinds of identifiers, but for efficiency should join by entity id.

## Schemas

Schema is represented as a map with three required attributes: `db/ident`, `:db/valueType`, and`:db/cardinality`.

Let's look at an example:

    {:db/id #db/id[:db.part/db  ]
     :db/ident :artist/gid
     :db/valueType :db.type/uuid
     :db/cardinality :db.cardinality/one
     :db/unique :db.unique/identity
     :db/index true
     :db/doc "The globally unique MusicBrainz ID for an artist"
     :db.install/_attribute :db.part/db}

Attributes are themselves _Entities_ with associated attributes.
 
A schema is immutable.

Notes:

- `#db/id` is a temp-id
- db.unique is enforced by the transactor
- valueType is a generic
- entity ids will change—don't rely on them; use `db.unique/identity`

## Transactions

Datomic transactions are ACID: Atomic, Consistent, Isolated, and Durable.

If you're performing CRUD operations, there is no `U` & `D` in Dataomic, only Create and Read. Update is considered as 'Retract'ing of an existing attribute.

Transaction requests are data structures. Each operation to the db is represented as a tuple of four things:

    [op entity-id attribute value]
    ;; op is one of :db/add and :db/retract.

Sample:

    {:db/id entity-id
     attribute value
     attribute value
     ... }

Create - `db:add`

To add data to an existing entity, build a transaction using :db/add implicitly with the map structure (or explicitly with the list structure). The following example shows creating data using a transaction:

    (d/transact conn
        [;; A user posting an article
          {:db/id #db/id [:db.part/user -100]
           :user/username "rich.hickey"}
          {:db/id #db/id [:db.part/user -200]
           :category/name "Functional Programming"}
          {:db/id #db/id [:db.part/user -300]
           :article/title "Scala, the bad parts"
           :article/author #db/id [:db.part/user -100]
           :article/category #db/id [:db.part/user -200]
           :article/body "Scala is ..."}])

Notes:

- Every tx is yet another datum
- `transact` takes a vector of vectors: [Op, Ent, Attr, Val]; consider it a 
  vector of assertions
- `transact` can be sync or async; both return a promise
- Sync transactions have an implicit timeout
- Single value vs multi-valued attribute => db cardinality
- Within a tx, a tempid (-100) denotes the same "new" entity; scope of a 
  tempid is a single tx
- Tx itself is an entity!
- Tuples within a tx is not ordered
- A single tx can and will contain multiple attribute changes

## Queries

Query is data. 

Queries and query results are represented as simple collection data structures that accept a find clause, where conditions, and optional attribute values. The syntax looks like this: find, in, and where 'data patterns':

Example: find all movie titles in the year 1987.

    [:find ?title
     :where 
        [?e :movie/year 1987]
        [?e :movie/title ?title]]

Variables are symbols: strings, numbers keywords, literals, and `#tags`

Datomic has implicit joins. You don't have to apply markers for join attributes: mention them in the next tuple.

    [:find ?attr
     :where
        [?p :person/name]
        [?p ?a]
        [?a :db/ident ?attr]]

In the query above, checking two entity ids (`?p`) in consecutive constraints fires off an implicit join. 

The order of the data patterns does not matter.

Datomic doesn't optimize query for the size of datasets. This strategy can be changed in code. Data is stored in chunks in a tree in storage and datalog retrieves chunks from the storage.

# Part II - Deep dive

## Parameterized Queries

With parameterized queries, you can pass scalars (basic types like String, Integer, etc.,), Tuples, Collections, and Relations as 'input' to queries.

**Scalars**

    [:find ?title
     :in $ ?name
     :where
       [$ ?p :person/name ?name]
       [$ ?m :movie/cast ?p]
       [$ ?m :movie/title ?title]]

**Tuples**

    [:find ?title
     :in $ [?director ?actor]
     :where
       [?d :person/name ?director]
       [?a :person/name ?actor]
       [?m :movie/director ?d]
       [?m :movie/cast ?a]
       [?m :movie/title ?title]]

**Collections**

    [:find ?title
     :in $ [?director ...]
     :where
       [?p :person/name ?director]
       [?m :movie/director ?p]
       [?m :movie/title ?title]]

**Relations**

Consider a relation `[title box-office-earnings]`:

    [["Die Hard" 140700000]
     ["Alien" 104931801]
     ["Lethal Weapon" 120207127]]

A query could be:

    [:find ?title ?box-office
     :in $ ?director [[?title ?box-office]]
     :where
       [?p :person/name ?director]
       [?m :movie/director ?p]
       [?m :movie/title ?title]]

## Expression Clauses

**Predicates**

    ;;[(predicate ...)]
    
    [:find ?e :where [?e :age ?a] [(< ?a 30)]]

**Functions**

    ;;[(function ...) bindings]

    (d/q 
      '[:find ?prefix
       :in [?word ...]
       :where 
          [(subs ?word 0 5) ?prefix]]
          ["hello" "clojurians"])

## Indexes

Indexes are the magic behind Datomic's performance. They're a sorted set of datums, and every datom is stored in two or more indexes:

EAVT, AEVT, AVET, VAET

- EAVT = row oriented, indexed by default; contains all datoms
- AEVT = column oriented; contains all datoms
- AVET = key-value; contains attributes that are db:indexed, :db/unique
- VAET = reverse index, traverse refs (db.type/ref)

In other words, Datomic allows several 'views' of the data:

- Rows      = datoms sharing a common E
- Columns   = datoms sharing a common A
- Document  = traversal of attributes
- Graph     = traversal of reference attributes

Indexes are stored in chunks, organized into trees. The leaf where the chunk is stored is a fressian encoded data. Every segment has a guuid whose value is a zipped data (chunk value).

## Caching

Datomic caches only immutable data, so all caches are valid forever.

## Constraints

- speculative, compare-set, transactor fn (macros!)->userful for atomic operations
- transactor fns are like macros for transactors.
- you can send arbitrary fn to transactors and let it run on peers.

## Dataflow

Five types of functions: transform, emit, derive, continue and effect. 

Transform and derive functions produce values which change the state of part of the information model. Derive and emit functions are called when parts of the information model, which are inputs to these functions, are changed. All of the dependencies between functions and the information model are described in a data structure.

Each is a pure function.

## Exceptions and Error codes

TODO

## Rules

Rules are analagous to functions. Instead of having forms, rules simply enclose a bunch of constraints (datalog). Abstracting reusable parts of queries into rules, enable query composition at will.

Consider this:

    [?p :person/name ?name]
    [?a :article/body ?p]
    [?a :article/title ?title]

Changed into a rule:

    [(blog-post ?name ?title)
     [?p :person/name ?name]
     [?a :article/body ?p]
     [?a :article/title ?title]]

Rule composition is powerful. Datalog rules can even implement graph traversal algorithms.

## Database Functions

TODO

# PART III - Architecture

## Peers

Peers handle queries and data storage.

Applications with Datomic code runs in Peer(s). They take care of queries, caching, and hide the details of synchronizing updates from other peers. 

Peers query and access data locally using a database value and cache data extensively using LRU cache, representing a partial copy of all the facts. They construct a value for a database at a particular moment in time.

Peers are independent and don’t affect each other. They write new facts by asking the Transactor to add them to the Storage Service, get notified by the Transactor about new facts, and add them to their caches.

Peers can partition the load into different peers by "type" of data/work. A production setup, typically, involves dynamically sharding data across peers.

## Transactors

A Transactor is a standalone process that handles transactions and indexing with _writes and retracts_. They are responsible for synchronizing data to Peers and implementing ACID transactions. Implemented as a "single thread of execution", with full serializabilty (isolation level: serialized), they write to a write-ahead log for durability. 

Basically a coordinator: all writes must go.

Typically, a transactor is deployed at the same location as peers, preferably close to the storage service. Transactors can be 1-2, run in serial sync, not parallel. During hot failover the 2nd transactor takes over (in a few seconds for pro); however queries continue to work.

Transactor owns the root index; indexes are global. A Transactor handles indexing; Indexes are generated so peers can use it.

- Careful with error vs timeout
- Can you pass around the 't' (in a cookie)? (Find out)
- Opaque binary blobs
- memcache--no invalidation

# Datomic MBrainz, revisited

A step-by-step guide to creating mbrainz datomic app, with datomic-tools.

TODO.

# Additional Notes

Datomic is information oriented and blobs are opaque to it.

Make data immutable, control *change*, understand it.

How? Queues and Messages.

Messages are data, Clojure maps.

In a Pedestal application, application state is stored in a tree. This tree is the application's **information model**.  The value of the model is immutable, so change takes the form of state transitions which are implemented as pure functions. The functions which perform these state transitions are run each time a new transform message is delivered on the input queue.

A **transform message** is any message which describes a change that the receiver should make. Transform messages will usually have a target, an operation to perform and some arguments.

Peers = app processes.

What is datamic db?
> references to index roots

Datomic's notion of time: when was this fact learned? Which means your notion of time may be different.

`Seek-datoms` - a lazy, raw access of datoms

An indexing job purges data from memory and creates a new tree that includes datums from the last indexing job.

# References

## Libraries

- [Docket-Datomic-free](https://github.com/tauho/docker-datomic-free)

## Books

## Articles & Blogs

- [Architecture](http://www.infoq.com/articles/Architecture-Datomic)
- [Information Model](http://www.infoq.com/articles/Datomic-Information-Model)
- [Overview](http://www.slideshare.net/StampedeCon/datomic-a-modern-database-stampedecon-2014)
- [Official blog](http://blog.datomic.com/?view=flipcard)

## Tutorials

- [Datomic Walkthrough](http://www.danneu.com/posts/authordb-datomic-tutorial/)
- [Day of Datomic](https://github.com/Datomic/day-of-datomic)
- [Datomic for five-year-olds](http://www.flyingmachinestudios.com/programming/datomic-for-five-year-olds/)
- [Interactive Datalog Tutorials](http://www.learndatalogtoday.org)
- [Simple Blog example](https://gist.github.com/a2ndrade/5651419)
- [MBrainz sample db](https://github.com/Datomic/mbrainz-sample)

## Videos

- [Introduction to Datomic, by Rich Hickey](http://docs.datomic.com/tutorial_video.html#!prettyPhoto/0/)
- [Datalog Queries](https://www.youtube.com/watch?v=bAilFQdaiHk)
- [Datomic Up & Running](https://www.youtube.com/watch?v=ao7xEwCjrWQ)