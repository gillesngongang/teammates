#Design

##Architecture

![High Level Architecture](images/highlevelArchitecture.png)

TEAMMATES is a Web app that runs on Google App Engine (GAE) cloud platform. Given above is an overview of the main components. 
+ **UI**: The UI seen by users consists of Web pages containing HTML, CSS (for styling) and JavaScripts (for client-side interactions such as sorting, input validation etc.). This UI is generated by JavaServer Pages (JSP), using the JavaServer Pages Standard Tag Library (JSTL), and Java Servlets residing on the server. Requests are sent to the server over normal HTTP. In future, AJAX may be used sparingly to fetch data from the server asynchronously.
+ **Logic**: The main logic of the app is in Java POJOs (Plain Old Java Objects). Some automated tasks are implemented as Servlets.
+ **Storage**: Storage uses the persistence framework provided by GAE 'datastore', a noSQL database.
+ **Test Driver**: TEAMMATES makes heavy use of automated regression testing. TestNG is used for Java unit testing and QUnit for JavaScript unit testing. We use Selenium Web Driver to automate UI testing. Test Driver can access the application directly to set up test data. We use JSON format to transmit such data to the application.
+ **Client**: The Client component can connect to the back end directly without using a Browser. It is used for administrative purposes. e.g., migrating data to a new schema
+ **Common**: The Common component contains utility code used across the application.

The diagram below shows how the code is organized into packages inside each component and dependencies among them.

![Package Diagram](images/packageDiagram.png)

Notes:
+ [logic] - [ui::view] - [ui::controller] represent an application of Model-View-Controller pattern.
+ ui::view and ui::website packages are not Java packages. They consist of jsp, html, js and css files.

##UI

The diagram below shows the object structure of the UI component.

![UI Component](images/UiComponent.png)

###Request flow

Request from the Browser for a specific page will go through following steps: 

![UI Workflow](images/UiWorkflow.png)

1. Request received by the GAE server.
2. Custom filters are applied according to the order specified in `web.xml`. In our case this would be `DatastoreFilter` and `AppstatsFilter`.
3. Request forwarded to the `ControllerServlet`.
4. `ControllerServlet` uses the `ActionFactory` to generate the matching `Action` object. E.g., `InstructorHomePageAction`.
5. `ControllerServlet` executes the action.
6. The `Action` object checks the access rights of the user. If the action is allowed, it interacts with the `Logic` component to perform the action.
7. Assuming the action was loading a page, the `Action` gathers the data required for the page into a `PageData` object. e.g. `InstructorHomePageData`, creates a `ShowPageResult` object by enclosing the `PageData` object created previously, and returns it to the `ControllerServlet`.
8. `ControllerServlet` "sends" the result. In the case of a `ShowPageResult`, this is equivalent to forwarding to the matching JSP page.
9. The JSP page uses the data in the given `PageData` object to generate the HTML page.
10. The response will then be sent back to the Browser, which will render the page.

Things to note: 

+   After performing certain actions, the Browser should load another page, possibly with the status of the previous action. For example, if the action is 'delete course', the Browser should load the 'courses' page after the server performed the action. In such cases, the result generated for the action will be of type `RedirectResult` which simply instructs the Browser to send a fresh request for the specified page. In such cases, we do not create a `PageData` object for the original request.  
  Example:
  - Browser request for 'delete course' action.
  - Server performs the action (creates a `RedirectResult` object but no `PageData` object) and instructs the Browser to load the 'courses' page.
  - As instructed, the Browser requests for the 'courses' page.
  - Server processes the request separately (creates a `ShowPageResult` object but no `PageData` object) and returns the 'courses' page.
+ The result of some actions is downloading of a file (e.g. a feedback session report). In such cases, the result type will be `FileDownloadResult` and no `PageData` object will be generated.
+ Since the high-level workflow of processing a request is same for any request, we use the , the [Template Method pattern](http://en.wikipedia.org/wiki/Template_method_pattern) to abstract the process folow into the `Action` class.
+ The list of actions and corresponding URIs are listed in the `ActionURIs` nested class of the [Const](../src/main/java/teammates/common/util/Const.java) class.
+ The list of pages and corresponding URIs are listed in the `ViewURIs` nested class of the [Const](../src/main/java/teammates/common/util/Const.java) class.

###Types of pages

The UI consist of following pages:
+ Product pages (functional): e.g., 'courses' page. These require login.
+ Product pages (peripheral): e.g., help pages, error pages. etc.
+ Website pages: These are the static pages of the product website. e.g., `contact.jsp`

##Logic

The `Logic` component handles the business logic of TEAMMATES. 
It is accessible via a thin [facade class](http://en.wikipedia.org/wiki/Facade_pattern) called [Logic](../src/main/java/teammates/logic/api/Logic.java) which makes use of several `*Logic` classes to handle the logic related to various types of data and to access data from the `Storage` component. In particular, `Logic` is responsible for these: 
+ Managing relationships between entities. e.g., cascade logic for create/update/delete.
+ Managing transactions. e.g., to ensure atomicity of a transaction.
+ Sanitizing input values recevied from the UI component.
+ Providing a mechanism for checking access control rights.

![Logic Component](images/LogicComponent.png)

Package overview:
+ **`logic.api`**: Provides the normal API of the component.
+ **`logic.backdoor`**: Provides a mechanism for the test driver to access data.
+ **`logic.core`**: Contains the core logic of the system.
+ **`logic.automated`**: Contains the logic of automated tasks.
+ **`logic.publicresource`**: Contains the logic for retrieving data without the need for authentication.

###Logic API

Represented by these classes:
+ `Logic`: For the use of the UI. Logic class acts as a facade between UI (servlets) and the backend of the app.
+ `GateKeeper`: For the use of the UI. To check the access rights of a user for a given action.
+ `BackDoorLogic`: For the use of `TestDriver` (via `BackDoorServlet`)

###Policies

General: 
+ Null values should **not** be used as parameters to this API, except when following the KeepExisting policy (explained later).

Access control:
+ Although this component provides methods to perform access control, the API itself is not access controlled. The UI is expected to check access control (using `GateKeeper` class) before calling a method in the `Logic`.
+ However, calls received by `BackDoorServlet` are authenticated using the 'backdoor key'. Backdoor key is a string known only to the person who deployed the app (typically, the administrator).

API for creating entities:
+ Null parameters: Causes an assertion failure.
+ Invalid parameters: Throws `InvalidParametersException`.
+ Entity already exists: Throws `EntityAlreadyExistsException` (escalated from Storage level).

API for retrieving entities:
+ Attempting to retrieve objects using `null` parameters: Causes an assertion failure.
+ Entity not found: 
  - Returns `null` if the target entity not found. This way, read operations can be used easily for checking the existence of an entity.
  - Throws `EntityDoesNotExistsExeption` if a parent entity of a target entity is not found e.g., trying to list students of a non-existent course.

API for updating entities:
+ Primary keys cannot be edited except: `Student.email`.
+ KeepExistingPolicy: the new value of an optional attribute is specified as `null` or set to “Uninitialized”, the existing value will prevail. {This is not a good policy. To be reconsidered}.
+ `Null` parameters: Throws an assertion error if that parameter cannot be null. Optional attributes follow KeepExistingPolicy.
+ Entity not found: Throws `EntityDoesNotExistException`.
+ Invalid parameters: Throws `InvalidParametersException`.

API for deleting entities:
+ `Null` parameters: Not expected. Results in assertion failure.
+ FailDeleteSilentlyPolicy: In general, delete operation do not throw exceptions if the target entity does not exist. Instead, it logs a warning. This is because if it does not exist, it is as good as deleted.
+ Cascade policy:   When a parent entity is deleted, entities that have referential integrity with the deleted entity should also be deleted.  
  Refer to the API for the cascade logic.

##Storage

The Storage component performs CRUD (Create, Read, Update, Delete) operations on data entities individually.

![Storage Component](images/StorageComponent.png)

Package overview:
+ **`storage.api`**: Provides the normal API of the component.
+ **`storage.entity`**: Classes that represent persistable entities.
+ **`storage.datastore`**: Classes for dealing with the datastore.
+ **`storage.search`**: Classes for dealing with searching and indexing.

Storage contains minimal logic beyond what is directly relevant to CRUD operations. 

In particular, it handles these:
+ Validating data inside entities before creating/updating them, to ensure they are in a valid state.
+ Hiding the complexities of datastore from the `Logic` component. All GQL queries are to be contained inside the `Storage` component.
+ Protecting persistable objects: Classes in the `storage::entity` package are not visible outside this component to prevent accidental modification to the entity's attributes (Since these classes have been marked as 'persistence capable', and changes to their attributes are automatically persisted to the datastore by default). 
Instead, a corresponding non-persistent [data transfer object](http://en.wikipedia.org/wiki/Data_transfer_object) named `*Attributes` (e.g., `CourseAttributes` is the data transfer object for `Course` entities) object is returned, where values can be modified easily without any impact on the persistent data copy. These datatransfer classes are in `common::datatransfer` package explained later. Note: This decision was taken before GAE started supporting [the ability to 'detach' entities](https://cloud.google.com/appengine/docs/java/datastore/jdo/creatinggettinganddeletingdata) to prevent accidental modifications to persistable data. The decision to use data transfer objects is to be reconsidered in the future.

The `Storage` component will not perform any cascade delete/create operations. Cascade logic is currently handled by the `Logic` component. 

Note that the navigability of the association links between entity objects appear to be in the reverse direction of what we see in a normal OOP design. This is because we want to keep the data scheme flexible so that new entity types can be added later with minimal modifications to existing elements.

###Policies

Add and Delete operations try to wait until data is persisted in the datastore before returning. This is not enough to compensate for eventual consistency involving multiple servers in the GAE production enviornment. However, it is expected to avoid test failures caused by eventual consistency in dev server and reduce such problems in the live server. 
Note: 'Eventual consistency' here means it takes some time for a database operation to propagate across all serves of the Google's distributed datastore. As a result, the data may be in an inconsistent states for short periods of time although things should become consistent 'eventually'. For example, an object we deleted may appear to still exist for a short while. 

Implementation of Transaction Control has been decided against due to limitations of GAE environment and the nature of our data schema. Please see [TEAMMATES Decision Analysis](https://docs.google.com/document/pub?id=1o6pNPshCp9S31ymHY0beQ1DVafDa1_k_k7bpxZo5GeU&embedded=true) document for more information. 

General: 
+ If `Null` is passed as a parameter, the corresponding value is **NOT** modified, as per the KeepExistingPolicy that was previously mentioned.

API for creating:
+ Attempt to create an entity that already exists: Throws `EntityAlreadyExistsException`.
+ Attempt to create an entity with invalid data: Throws `InvalidParametersException`.

API for retrieving:
+ Attempt to retrieve an entity that does not exist: Returns `null`.

API for updating:
+ Attempt to update an entity that does not exist: Throws `EntityDoesNotExistException`.
+ Attempt to update an entity with invalid data: Throws `InvalidParametersException`.

API for deleting:
+ Attempt to delete an entity that does not exist: Fails silently.

##Common

The Common component contains common utilities used across TEAMMATES.

![Common Component](images/CommonComponent.png)

Package overview:
+ **`common.util`**: Contains utility classes.
+ **`common.exceptions`**: Contains custom exceptions.
+ **`common.datatransfer`**: Containts data transfer objects. Given below are some more information about this package.

`common.datatransfer` package contains lightweight 'data transfer object' classes for transferring data among components. They can be combined in various ways to transfer structured data between components. Given below are three examples. 

![Data Transfer Classes](images/dataTransferClasses.png)

  (a) `Test Driver` can use the `DataBundle` in this manner to send an arbitrary number of objects to be persisted in the database.  
  (b) This structure can be used to transfer data of a course (e.g., when constructing the home page for an instructor).  
  (c) This structure can be used to send results of a feedback session (e.g., when showing a feedback session report to an instructor).   

For convenience, these classes use public variables for data. This is not a good practice as it contravenes OO principle of _information hiding_ and increases the risk of inconsistent data. This strategy is to be reconsidered at a later date. 

##TestDriver

This component automates the testing of TEAMMATES.

![Test Driver Component](images/TestDriverComponent.png)

Package overview:
+ **`test.driver`**: Contains infrastructure need for running the test driver.
+ **`test.pageobjects`**: Contains abstractions of the pages as the appear on a Browser (i.e. SUTs).
+ **`test.util`**: Contains helper methods.
+ **`test.cases`**:   Contains test cases.  
  Sub packages:  
 - **`.cases.driver`**: Component test cases for testing test driver infrastructure.
 - **`.cases.automated`**: Component test cases for testing some automated tasks.
 - **`.cases.common`**: Component test cases for testing the `Common` component.
 - **`.cases.logic`**: Component test cases for testing the `Logic` component.
 - **`.cases.storage`**: Component test cases for testing the `Storage` component.
 - **`.cases.ui`**: System test cases for testing the UI.

Notes:
+ Component tests: Some of these are pure unit tests (i.e., test one component in isolation) while others are integration tests that tests units as well as integration of units with each other.
+ `AllJsTests.java` (implemented as a UI test) is for unit testing JavaScript code.

This is how TEAMMATES testing maps to standard types of testing. 

```
Normal
|---------acceptance tests----|---system tests----|-----integration tests-----|------unit tests---------|
|-------------manual testing-------------|----automated UI tests----|-----automated component tests-----|
TEAMMATES
```

##Client

The Client component contains scripts that can connect to the application backend for things such as migrating data and calculating statistics.

Package overview:

+ **`client.remoteapi`**: Classes needed to connect to the backend directly.
+ **`client.scripts`**: Scripts that do things with the back end data.