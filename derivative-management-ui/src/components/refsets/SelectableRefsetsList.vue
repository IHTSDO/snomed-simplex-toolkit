<template>
  <v-card
    outlined
  >
    <v-list>
      <v-list-item-group v-model="model" multiple>
        <v-list-item two-line
          v-for="(refset, i) in refsets"
          :key="i"
        >
          <template v-slot:default="{ active }">
            <v-list-item-action>
              <v-checkbox :input-value="active"></v-checkbox>
            </v-list-item-action>

            <v-list-item-content>
              <v-list-item-title v-text="refset.name"></v-list-item-title>
              <v-list-item-subtitle v-text="refset.subtitle"></v-list-item-subtitle>
            </v-list-item-content>
          </template>
        </v-list-item>
      </v-list-item-group>
    </v-list>
  </v-card>
</template>

<script>
  export default {
    name: 'SelectableRefsetsList',
    data: () => ({
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
        var result = [];
        val.forEach((x) => result.push(this.refsets[x]));
        this.$emit('selectedRefsets', result)
      }
    }
  }
</script>