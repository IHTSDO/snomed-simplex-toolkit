# Simplex Toolkit
A toolkit to aid maintenance of simple SNOMED CT Extensions. 
Spreadsheets can be used to create and update SNOMED CT resources within 
an instance of the Snowstorm terminology server. 
From there they can be versioned and exported using the RF2 distribution standard.

This project is built on the Java Spring Boot framework.

## Build
Build the project on the command line using Maven:
```
mvn clean package
```
## Run
Start the application on the command line using Java:
```
java -Xms2g -jar target/derivative-management-tool*.jar
```
Once the toolkit has started navigate to http://localhost:8081/ to use the user interface.
