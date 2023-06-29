<template>
    <v-container>
        <v-row>
            <h3 class="mt-4">SNOMED CT Edition Details</h3>
        </v-row>
        <v-row>
          <v-col>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >Module Name: <b>{{codeSystem.defaultModuleDisplay}}</b></v-list-item-title>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >Module ID: <b>{{codeSystem.defaultModule}}</b></v-list-item-title>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >SNOMED CT URI identifier: <b>http://snomed.info/sct/{{ codeSystem.defaultModule }}</b></v-list-item-title>
            <i><a href="http://snomed.org/uri" target="_blank">SNOMED CT URI standard</a></i>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
          </v-col>
        </v-row>
    </v-container>
</template>

<script>
  export default {
    name: 'EditionInfo',
    props: {
      codeSystem: Object
    }
  }
</script>
