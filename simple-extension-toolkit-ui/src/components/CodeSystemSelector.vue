<template>
  <v-container
  >
    <h2>Select a Code System to work with</h2>
    <v-list
     style="max-height: 400px"
       class="overflow-y-auto">
      <v-list-item-group v-model="selected">
        <v-list-item two-line
          v-for="(codesystem, i) in codesystems"
          :key="i"
        >
          <v-list-item-content>
            <v-list-item-title v-text="codesystem.name"></v-list-item-title>
            <v-list-item-subtitle>{{codesystem.shortName}}</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list-item-group>
    </v-list>
  </v-container>
</template>

<script>
  export default {
    data: () => ({
      name: 'CodeSystemSelector',
      selected: null
    }),
    props: {
      codesystems: []
    },
    watch: {
      selected: function(val) {
        this.$emit('selectedCodeSystem', this.codesystems[val])
      }

    }
  }
</script>