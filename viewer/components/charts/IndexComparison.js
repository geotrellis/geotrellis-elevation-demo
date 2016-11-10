import React from 'react';
import { render } from 'react-dom';
import shortid from 'shortid';
import _ from 'lodash';
import Loader from 'react-loader';

var IndexComparison = React.createClass({
  getInitialState: function () {
    return { loaded: false };
  },
  _fetchPolygonalSummary: function(polyLayer, readerType, layerType) {
    let root = polyLayer.chartProps.rootURL;
    let layerName = polyLayer.chartProps.layerName;
    let latlng = polyLayer._latlng;
    let url = `${root}/mean/${layerName}/${readerType}`;

    return fetch(url, {
      method: 'POST',
      body: JSON.stringify(polyLayer.toGeoJSON().geometry)
    }).then( response => {
      response.json().then( summary => {
        var data = summary.answer;

        if (layerType == 'intraLayerDiff') {
          polyLayer.comparisonStats[readerType] = data;
        } else {
          polyLayer.stats[readerType] = data;
        }
        this.setState({ loaded: true });
        this._renderChart(polyLayer, readerType, layerType);
      });
    },
    error => {});
  },
  _fillBox: function(ctx, value, ndi) {
      ctx.textAlign = 'center';
      ctx.fillText(value, 150, 30);
  },
  _renderChart: function(polyLayer, ndi, layerType) {
    let ctx = document.getElementById("canvas").getContext('2d');
    let canvas = {
      width: 300,
      height: 200
    };
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (layerType == 'intraLayerDiff') {
      this._fillBox(ctx, polyLayer.comparisonStats[ndi], ndi);
    } else {
      this._fillBox(ctx, polyLayer.stats[ndi], ndi);
    }
    ctx.fillStyle = '#000000';
    ctx.font = '18px Arial';
  },
  componentDidMount: function() {
    if (this.props.layerType === 'intraLayerDiff') {
      if (! this.props.poly.comparisonStats[this.props.ndi]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(this.props.poly, this.props.readerType, this.props.layerType);
      } else {
        this.setState({ loaded: true });
        this._renderChart(this.props.poly, this.props.readerType, this.props.layerType);
      }
    } else {
      if (! this.props.poly.stats[this.props.readerType]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(this.props.poly, this.props.readerType, this.props.layerType);
      } else {
        this.setState({ loaded: true });
        this._renderChart(this.props.poly, this.props.readerType, this.props.layerType);
      }
    }
  },
  componentWillReceiveProps: function(nextProps) {
    if (nextProps.layerType === 'intraLayerDiff') {
      if (! nextProps.poly.comparisonStats[nextProps.readerType]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(nextProps.poly, nextProps.readerType, nextProps.layerType);
      } else if (this.state.loaded) {
        this._renderChart(nextProps.poly, nextProps.readerType, nextProps.layerType);
      }
    } else {
      if (! nextProps.poly.stats[nextProps.readerType]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(nextProps.poly, nextProps.readerType, nextProps.layerType);
      } else if (this.state.loaded) {
        this._renderChart(nextProps.poly, nextProps.readerType, nextProps.layerType);
      }
    }
  },
  render: function() {
    let loading = this.state.loaded ? null : (<p>Loading data...</p>);
    return (
      <div>
        {loading}
        <canvas id="canvas" width={300} height={200} hidden={! this.state.loaded}/>
      </div>
    );
  }
});

module.exports = IndexComparison;
