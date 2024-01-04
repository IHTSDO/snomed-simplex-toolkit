# Simplex Toolkit
A toolkit to aid the maintenance of SNOMED CT extensions. 

The toolkit provides an interface between common formats like spreadsheets and a Snowstorm authoring terminology server. 
Once the content is in the terminology server it can be versioned and an standard RF2 extension exported for use in any other terminology server that supports SNOMED CT. 

## Features
### General Behaviour
  - Content synchronized with SNOMED CT components in Snowstorm
  - Components (concepts, descriptions, refset members) have no effective time until extension is versioned
  - Redundant components will be deleted or inactivated depending on release status
  - Extension RF2 can be exported at any time 
### Simple Reference Sets
  - Creation of a concept to represent a simple refset (with parent `446609009 |Simple type reference set (foundation metadata concept)|`)
  - Download of current refset members as Spreadsheet (blank initially)
  - Maintain members by uploading an edited version of the Spreadsheet containing a new set of concept codes
### Snap-2-SNOMED Maps / Simple Map to SNOMED CT with Correlation
  - Creation of a concept to represent a map refset (with parent `1193543008 |Simple map with correlation to SNOMED CT type reference set (foundation metadata concept)|`)
  - Download of current map members as Spreadsheet (blank initially)
  - Maintain map members by uploading a Spreadsheet export from [Snap2SNOMED](https://snap.snomedtools.org/)
    - Only map entries with a status of "ACCEPTED" will be put into the extension
### Weblate Translations
  - Creation of a concept to represent a translation / language refset (with parent `900000000000506000 |Language type reference set (foundation metadata concept)|`)
  - Upload translation from [Weblate](https://translate.snomedtools.org/)
  - Maintain translation by uploading updated translation export
  - First translated term will be made the Preferred Term, any other terms will be acceptable synonyms

## Prerequisites
Java 17 is required. 
To maintain an extension write access to a [Snowstorm terminology server](https://github.com/IHTSDO/snowstorm) is required.
A code system must be created in Snowstorm for the extension. The code system branch must have a `defaultModuleId` set in the branch metadata.
The Simplex toolkit will be enhanced in the future to help create and set up new extensions in Snowstorm.

## Build or Download
Build the project on the command line using Maven to create the jar file in the target directory.
```
mvn clean package
```
Or alternatively download the latest pre-built jar file from the GitHub releases page.

## Configure
The toolkit assumes that Snowstorm will be running on the same machine on port 8080. To change any of the default configuration options create an `application.properties` file in 
the same folder as the jar file. Then copy in any lines that need to be changed from the default configuration file 
[src/main/resources/**application.properties**](/src/main/resources/application.properties).  

## Run
Start the application on the command line using Java:
```
java -Xms2g -jar api/snomed-simplex-toolkit*.jar
```
Once the application has started the user interface will be available at http://localhost:8081/simplex-toolkit/

## SNOMED International Authentication
The following is required to run against SNOMED International production infrastructure:
- Host the Simplex Toolkit on an SSL host within the domain ihtsdotools.org for the browser to send the cookie to be forwarded to Snowstorm
- Config: `security-token-header=CookieName:ims-ihtsdo`
