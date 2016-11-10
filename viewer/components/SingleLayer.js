"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
import ifAllDefined from "../utils/utils";

function updateSingleLayerMap (showLayer, root, layer) {
  showLayer(`${root}/tiles/${layer.name}/{z}/{x}/{y}`);
};

var SingleLayer = React.createClass({
  getInitialState: function () {
    return {
      readerType: "rdd",
      operation: "none",
      layerId: undefined // layer index
    };
  },
  handleLayerSelect: function(ev) {
    let layerId = +ev.target.value;
    let newState = _.merge({}, this.state, {
      "layerId": layerId
    });

    this.setState(newState);
    this.props.setLayerName(this.props.layers[layerId]);
    this.updateMap(newState);
    this.props.showExtent(this.props.layers[layerId].extent);
  },
  updateState: function(target, value) {
    let newState = _.merge({}, this.state, {[target]: value});
    this.setState(newState);
    this.updateMap(newState);
  },
  ping: function () {
      alert("PING");
  },
  updateMap: function (state) {
    if (! state) { state = this.state; }
    ifAllDefined(
      this.props.showLayer,
      this.props.rootUrl,
      this.props.layers[state.layerId]
    )(updateSingleLayerMap);
    this.props.showExtent(this.props.layers[state.layerId].extent);
  },
  componentWillReceiveProps: function (nextProps){
  /** Use this as an opportunity to react to a prop transition before render() is called by updating the state using this.setState().
    * The old props can be accessed via this.props. Calling this.setState() within this function will not trigger an additional render. */
    if ( _.isUndefined(this.state.layerId) && ! _.isEmpty(nextProps.layers)) {
      // we are blank and now is our chance to choose a layer
      let newState = _.merge({}, this.state, { layerId: 0 });
      let layer = nextProps.layers[0];
      this.setState(newState);
      updateSingleLayerMap(nextProps.showLayer,
                           nextProps.rootUrl,
                           layer);
      nextProps.showExtent(layer.extent);
    }
  },
  render: function() {
    let layer       = this.props.layers[this.state.layerId];

    let layerOptions =
      _.map(this.props.layers, (layer, index) => {
        return <option value={layer.name} key={index}>{layer.name}</option>;
      });

    return (
      <div>
        <Input type="select" label="Layer" placeholder="select" value={this.state.layerId}
          onChange={e => this.handleLayerSelect(e)}>
          {layerOptions}
        </Input>
      </div>
    );
  }
});

module.exports = SingleLayer;
