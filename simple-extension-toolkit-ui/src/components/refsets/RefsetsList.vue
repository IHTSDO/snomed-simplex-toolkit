<template>
  <v-card
    outlined
  >
    <v-list v-if="mode == 'refsets'" 
     style="max-height: 400px"
       class="overflow-y-auto">
      <v-list-item-group v-model="model">
        <v-list-item
          v-if="refsets.length == 0">
          <v-list-item-content>
            <v-list-item-title>- None exist yet -</v-list-item-title>
          </v-list-item-content>
        </v-list-item>
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