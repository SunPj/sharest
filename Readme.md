# Sharest (SHAring via REST)

Creates REST API for your source (DB, memory, etc) with a few lines of code and without code duplication

### Examples

```scala
SupportedCollections(
    "posts" -> fromAnormTable("posts", db).toCollection,
)
```
This code will create following API endpoints    
1. Fetch post by id (HTTP GET `/api/collections/posts/<id>`) 
2. Fetch list of posts. Supports paging, sorting and filtering by all columns (HTTP GET `/api/collections/posts`) 
3. Create a post (HTTP POST `/api/collections/posts`)
4. Delete a post (HTTP DELETE `/api/collections/posts/<id>`)
5. Update a post (HTTP PATCH `/api/collections/posts/<id>`)  

#### Specific methods can be disabled for collection

```scala
SupportedCollections(
  "posts" -> fromAnormTable("posts", db).withAccessSettings.disableCreate().disableDelete().toCollection,
)
```
The same as example above, but CREATE and DELETE methods are disabled

#### Each of GET, FETCH, CREATE, DELETE and UPDATE methods can be configured

This sample below shows how to configure a FETCH method. Using this configuration FETCH API endpoin
will only return items with `id` and `name` fields
```scala
SupportedCollections(
    "posts" -> fromAnormTable("posts", db)
      .withFetchRules(
        FetchRules[StringTypes.type](
          fieldAllowed = ((_: RequestHeader) => Future.successful(Set("id", "name"))).some
        )
      )
      .toCollection
)
```

### Motivation

Say you have a table `posts` and you want to create a simple CRUD application,
given plain `Play` set up, the plan would be
1. Create a PostsDao with something like 
```scala
class PostsDao() {
  
  def create(postCreateModel: PostCreateModel): Future[Option[Post]] = ???
  
  def get(id: Int): Future[Option[Post]] = ???
  
  def update(id: Int): Future[Option[Int]] = ???
  
  def delete(id: Int): Future[Option[Int]] = ???
  
  def fetchPosts(filter: Filter, sort: Sort, paging: Paging): Future[Option[Post]] = ???
}
```

2. Create a Controller 
```scala
import play.api.mvc.AbstractController
class PostsCrudController @Inject()(
  postDao: PostDao,
  cc: ControllerComponents
) extends AbstractController(cc) {
  
  def get(id: Int): Future[Option[Post]] = Action.async {
    postDao.get(id).fold(NotFound)(Ok(_))
  }

  def update(id: Int): Future[Option[Int]] = Action.async {
    ???
  }

  def delete(id: Int): Future[Option[Int]] = Action.async {
    ???
  }
  
  def create(): Future[Option[Int]] = Action.async {
    ???
  }

  def fetchPosts(filter: Filter, sort: Sort): Future[Option[Post]] = Action.async {
    ???
  }
}
```

3. Introduce appropriate routes in `routes` file
```
GET    /posts PostsCrudController.fetch()
GET    /posts/:id PostsCrudController.get(id)
POST   /posts PostsCrudController.create()
PATCH  /posts/:id PostsCrudController.update(id)
DELETE /posts/:id PostsCrudController.delete(id)
```

This sounds like a lot of boilerplate, doesn't it? 
It will be doubled if we have 2 tables and can become unmanageable once we have 
10 and more of such tables. So we are here to help you with that ;)

### How to use this library?
Add to dependencies 
```sbt
resolvers += "Sharest GitHub Package Registry" at "https://maven.pkg.github.com/sunpj/commons"

libraryDependencies += "com.github.sunpj" %% "sharest" % "0.0.1"
```

Add routing mapping to your `routes` file

```
-> /collections            api.Routes
```

Configure the collections you want to expose

```scala
import com.google.inject._
import net.codingwell.scalaguice.ScalaModule
import com.github.sunpj.sharest.services.collection.SupportedCollections
import play.api.db.{Database, NamedDatabase}
import play.api.mvc.RequestHeader
import play.api.db.{Database, NamedDatabase}
import scala.concurrent.{ExecutionContext, Future}
import com.github.sunpj.sharest.services.collection.builder.source.anorm.StringTypes
import com.github.sunpj.sharest.services.collection.builder.{FetchRules, GetRules}
import com.github.sunpj.sharest.services.collection.builder.CollectionRulesBuilder._

import rest.builder.CollectionRulesBuilder._

class RestAPIModule extends AbstractModule with ScalaModule {

  @Provides
  def providesSupportedCollections(@NamedDatabase("default") db: Database)(implicit ec: ExecutionContext) = SupportedCollections(
    // exposes GET, FETCH, CREATE, DELETE, UPDATE REST API methods for table
    "posts" -> fromAnormTable("posts", db).toCollection,
    // exposes GET, FETCH, CREATE, UPDATE REST API methods for table (DELETE method is disabled)
    "stats" -> fromAnormTable("stats", db).withAccessSettings.disableCreate().disableDelete().toCollection,
    // exposes GET, FETCH, CREATE, UPDATE, DELETE REST API methods for table, while GET method will expose only id and title fields
    "news" -> fromAnormTable("news", db).withGetRules(GetRules[StringTypes.type](
      ((_: RequestHeader) => Future.successful(Set("id", "title"))).some,
    )).toCollection
  )

}
```

## Architecture   

This library brings ready to go routes, controller and collection service, and
leaves it to you to specify the list of collections you would like to be available via API.

You can think about a Collection as a database table (but it can be anything like scala List, file source, etc)

Collection can be created implementing `Collection` trait, but there are some high level builder
which makes creation of collection easier, `CollectionRulesBuilder` allows to specify the rules and build a `Collection` based on them 

### Showcases

You can find more cases in test cases **TODO add here a link to specs**
