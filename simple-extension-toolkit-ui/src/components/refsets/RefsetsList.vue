<template>
  <v-card
    outlined
  >
    <v-list v-if="mode == 'refsets'" 
     style="max-height: 400px"
       class="overflow-y-auto">
      <v-list-item-group v-model="model">
        <v-list-item two-line
          v-for="(refset, i) in refsets"
          :key="i"
        >
          <v-list-item-content>
            <v-list-item-title v-text="refset.pt.term"></v-list-item-title>
            <v-list-item-subtitle>{{refset.activeMemberCount}} active members</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list-item-group>
    </v-list>
    <v-list v-if="mode == 'maps'">
      <v-list-item-group v-model="model">
        <v-list-item two-line
          v-for="(map, i) in maps"
          :key="i"
        >
          <v-list-item-content>
            <v-list-item-title v-text="map.name"></v-list-item-title>
            <v-list-item-subtitle v-text="map.subtitle"></v-list-item-subtitle>
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
      maps: [
        {name: 'COVID Vaccines to Drug Dictionary', subtitle: '14 concepts, last update Dec. 12th 2021'},
        {name: 'Surgical procedures to National Procedures Terminology', subtitle: '356 concepts, last update Dec. 12th 2021'},
        {name: 'Imaging procedures to National Procedures Terminology', subtitle: '187 concepts, last update Dec. 12th 2021'},
      ]
    }),
    props: {
      mode: {
        type: String,
        default: 'refsets'
      },
      refsets: {}
    },
    watch: {
      model: function(val) {
        if (this.mode == 'refsets') {
          this.$emit('selectedRefset', this.refsets[val])
        } else if (this.mode == 'maps') {
          this.$emit('selectedRefset', this.maps[val])
        }
      }
    }
  }
</script>