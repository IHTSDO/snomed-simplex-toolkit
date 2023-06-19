<template>
    <v-container>
        <v-row>
            <h3 class="mt-4">SNOMED CT Module</h3>
        </v-row>
        <v-row>
            <v-col cols="12" md="6">
                The module for this Edition is: {{ codeSystem.defaultModule }} |{{ codeSystem.defaultModuleDisplay }}|
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
