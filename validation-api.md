# Validation API

## Run validation
`POST /api/codesystems/{codeSystem}/validate`

## Check validation status using a property of the codesystem
`GET /api/codesystems/{codeSystem}`

## List validation issues
`GET /api/codesystems/{codeSystem}/validate/issues`

Response format:
```json
{
  "errorCount": 2,
  "warningCount": 3,
  "fixes": [
    {
      "type": "automatic-fix",
      "subtype": "set-description-case-sensitive",
      "components": [
        {
          "conceptId": "123",
          "componentId": "456"
        },
        {
          "conceptId": "123",
          "componentId": "456"
        }
      ]
    },
    {
      "type": "user-fix",
      "subtype": "edit-or-remove-duplicate-term-different-concepts",
      "components": [
        {
          "conceptId": "1230",
          "componentId": "4560",
          "otherConceptId": "100"
        },
        {
          "conceptId": "1230",
          "componentId": "4560",
          "otherConceptId": "200"
        }
      ]
    }
  ]
}
```

## Save custom concept change
`PUT /api/{codeSystem}/concepts/{conceptId}`

Request format:
```json
{
  "parentCode": "3000000",
  "inactive": false,
  "langRefsetTerms": {
    "900000000000509007" : ["The PT", "A synonym", "Another term"],
    "12000000" : ["Patient friendly PT"]
  }
}
```

## Save subset change
### Delete subset member
`DELETE /api/{codeSystem}/refsets/simple/{refsetId}/members/{conceptId}`
### Add subset member
`PUT /api/{codeSystem}/refsets/simple/{refsetId}/members/{conceptId}`
