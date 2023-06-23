<template>
    <v-container>
        <v-row>
            <h3 class="mt-4">SNOMED CT Module</h3>
        </v-row>
        <v-row>
          <v-col>
                The module for this Edition is: <b><i>{{ codeSystem.defaultModule }} |{{ codeSystem.defaultModuleDisplay }}|</i></b>
            </v-col>
        </v-row>
        <v-row>
          <v-col>
            The <a href="http://snomed.org/uri" target="_blank">SNOMED CT URI identifier</a> for this Edition is: <b><i>http://snomed.info/sct/{{ codeSystem.defaultModule }}</i></b><br/>
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
