<template>
  <v-card
    outlined
  >
    <v-list>
      <v-list-item-group v-model="model">
        <v-list-item two-line
          v-for="(refset, i) in refsets"
          :key="i"
        >
          <v-list-item-content>
            <v-list-item-title v-text="refset.name"></v-list-item-title>
            <v-list-item-subtitle v-text="refset.subtitle"></v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list-item-group>
    </v-list>
  </v-card>
</template>

<script>
  export default {
    data: () => ({
      name: 'RefsetsList',
      model: null,
      refsets: [
        {name: 'COVID Vaccines', subtitle: '14 concepts, last update Dec. 12th 2021'},
        {name: 'COVID Symptoms', subtitle: '72 concepts, last update Dec. 12th 2021'},
        {name: 'COVID PPEs', subtitle: '8 concepts, last update Dec. 12th 2021'},
        {name: 'Imaging procedures', subtitle: '187 concepts, last update Dec. 12th 2021'},
        {name: 'Lab procedures', subtitle: '345 concepts, last update Dec. 12th 2021'},
        {name: 'Cardiology', subtitle: '890 concepts, last update Dec. 12th 2021'},
      ]
    }),
    watch: {
      model: function(val) {
        this.$emit('selectedRefset', this.refsets[val])
      }
    }
  }
</script>