(() => {
  if (window.__epicDashT4TuningInstalled) return;
  window.__epicDashT4TuningInstalled = true;

  const main = document.querySelector('main');
  if (!main) return;

  const style = document.createElement('style');
  style.id = 't4-tuning-workspace-style';
  style.textContent = `
    .t4tw-shell{height:100%;display:flex;flex-direction:column;gap:10px;padding:8px;box-sizing:border-box}
    .t4tw-head{display:flex;gap:10px;align-items:center;flex-wrap:wrap;background:#0c151d;border:1px solid var(--line);border-radius:14px;padding:10px}
    .t4tw-title{font-size:19px;font-weight:900;letter-spacing:.5px}.t4tw-badge{font-size:10px;font-weight:900;letter-spacing:1.2px;padding:5px 8px;border:1px solid #2f7f62;border-radius:99px;color:#78efb5}
    .t4tw-badge.sim{border-color:#6f5b24;color:#ffd86b}.t4tw-meta{color:var(--muted);font-size:11px;flex:1;min-width:240px}
    .t4tw-controls{display:flex;gap:7px;align-items:center;flex-wrap:wrap}.t4tw-search{min-width:220px;flex:1;background:#081018;border:1px solid var(--line);color:#eef7fc;border-radius:10px;padding:9px 11px;font-size:14px}
    .t4tw-btn{border:1px solid var(--line);background:#101c25;color:#dbeaf1;border-radius:10px;padding:8px 10px;font-weight:800}.t4tw-btn.active{border-color:var(--cyan);color:var(--cyan)}.t4tw-btn.t6{border-color:#94535a;background:#32161a;color:#ffd8dc}.t4tw-btn.t6.done{border-color:#2f7f62;background:#10241c;color:#78efb5}
    .t4tw-filters{display:flex;gap:6px;flex-wrap:wrap}.t4tw-filter{border:1px solid var(--line);background:#0b151d;color:var(--muted);border-radius:99px;padding:6px 11px;font-size:11px;font-weight:900;letter-spacing:.4px}.t4tw-filter.active{color:#fff;border-color:var(--cyan);background:#102430}
    .t4tw-modes{display:flex;gap:6px;flex-wrap:wrap}.t4tw-mode{border:1px solid var(--line);background:#0b151d;color:var(--muted);border-radius:10px;padding:7px 12px;font-size:11px;font-weight:900;letter-spacing:.4px}.t4tw-mode.active{color:#fff;border-color:#5286a4;background:#132633}
    .t4tw-summary{color:var(--muted);font-size:11px;white-space:pre-wrap}.t4tw-list{min-height:0;overflow:auto;display:grid;gap:5px;align-content:start}
    .t4tw-row{display:grid;grid-template-columns:42px minmax(180px,1fr) minmax(160px,.55fr) minmax(160px,.55fr);gap:8px;align-items:center;border:1px solid var(--line);background:linear-gradient(145deg,#101a22,#0a1117);border-radius:11px;padding:8px 10px;cursor:pointer}
    .t4tw-row:hover{border-color:#35576b}.t4tw-row.pending{border-color:#6f5b24}.t4tw-star{border:0;background:transparent;color:#60717e;font-size:19px;padding:4px;cursor:pointer}.t4tw-star.on{color:#ffd43b}
    .t4tw-name{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;overflow-wrap:anywhere}.t4tw-value{font-size:18px;font-weight:900;text-align:right}.t4tw-unit{color:var(--orange);font-size:11px;margin-left:4px}
    .t4tw-range{color:var(--muted);font-size:11px;text-align:right}.t4tw-simvalue{color:#ffd86b}.t4tw-tag{font-size:9px;border:1px solid #6f5b24;color:#ffd86b;border-radius:99px;padding:2px 6px;margin-left:7px;font-family:system-ui,sans-serif}
    .t4tw-empty{border:1px dashed var(--line);border-radius:12px;padding:18px;color:var(--muted);text-align:center}
    .t4tw-modal{position:fixed;inset:0;z-index:10040;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:18px}.t4tw-modal[hidden]{display:none}
    .t4tw-dialog{width:min(680px,96vw);max-height:88vh;overflow:auto;background:#0b141c;border:1px solid #315064;border-radius:16px;box-shadow:0 20px 70px rgba(0,0,0,.55);padding:16px}
    .t4tw-dialog-head{display:flex;gap:12px;align-items:flex-start}.t4tw-dialog-title{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:17px;font-weight:900;overflow-wrap:anywhere;flex:1}.t4tw-close{border:1px solid var(--line);background:#101c25;color:#fff;border-radius:9px;padding:6px 10px;font-weight:900}
    .t4tw-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:14px 0}.t4tw-stat{background:#091119;border:1px solid var(--line);border-radius:11px;padding:10px}.t4tw-stat small{display:block;color:var(--muted);font-size:10px;text-transform:uppercase}.t4tw-stat b{display:block;font-size:17px;margin-top:5px}
    .t4tw-inputrow{display:flex;gap:8px;align-items:center}.t4tw-input{flex:1;background:#061018;border:1px solid var(--line);border-radius:10px;padding:10px;color:#fff;font-size:17px;font-weight:800}.t4tw-status{margin-top:10px;padding:9px 10px;border-radius:10px;background:#081018;border:1px solid var(--line);font-size:12px;color:var(--muted)}.t4tw-status.good{border-color:#2f7f62;color:#78efb5}.t4tw-status.bad{border-color:#8b3e46;color:#ff9ca7}.t4tw-realwrite{margin-top:10px;width:100%;border-color:#94535a;background:#32161a;color:#ffd8dc}.t4tw-realwrite[hidden]{display:none}
    .t4tw-changes{display:grid;gap:7px}.t4tw-change{display:grid;grid-template-columns:minmax(180px,1fr) auto auto;gap:10px;align-items:center;border:1px solid var(--line);border-radius:10px;padding:9px}.t4tw-change-name{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}.t4tw-arrow{color:var(--muted)}
    .t4tw-arrayrow{display:grid;grid-template-columns:42px minmax(190px,1fr) 100px 130px 130px;gap:10px;align-items:center;border:1px solid var(--line);background:linear-gradient(145deg,#101a22,#0a1117);border-radius:11px;padding:9px 11px;cursor:pointer}.t4tw-arrayrow:hover{border-color:#35576b}.t4tw-arrayrow.pending{border-color:#6f5b24}.t4tw-arraymeta{color:var(--muted);font-size:11px;text-align:right}.t4tw-arraygrid{display:grid;gap:5px;overflow:auto;max-height:52vh;margin:12px 0;padding:2px}.t4tw-cell{min-width:68px;border:1px solid var(--line);background:#081018;color:#eaf4f8;border-radius:8px;padding:7px 5px;text-align:center;font:700 11px ui-monospace,SFMono-Regular,Menlo,monospace;cursor:pointer}.t4tw-cell:hover{border-color:#4f7c95}.t4tw-cell.selected{outline:2px solid var(--cyan)}.t4tw-cell.pending{border-color:#6f5b24;color:#ffd86b}.t4tw-cell.axis{cursor:default;background:#101b23;color:#9db1bc;border-color:#263b48;font-weight:800}.t4tw-cell.axis:hover{border-color:#263b48}.t4tw-cell small{display:block;color:var(--muted);font-size:8px;font-family:system-ui,sans-serif;margin-bottom:2px}
    .t4tw-regionbar{display:flex;gap:7px;align-items:center;flex-wrap:wrap;margin:9px 0;padding:9px;background:#081018;border:1px solid var(--line);border-radius:11px}.t4tw-regionmeta{color:#9db1bc;font-size:11px;font-weight:800;min-width:165px}.t4tw-regionvalue{width:105px;flex:0 0 105px}.t4tw-regionselect{background:#061018;border:1px solid var(--line);border-radius:9px;padding:8px;color:#fff;font-weight:800}.t4tw-cell.multiselected{background:#102b39;border-color:#4f8aaa}.t4tw-cell.axis.selectable{cursor:pointer}.t4tw-cell.axis.selectable:hover{border-color:var(--cyan);color:#dff8ff}
    .t4tw-settings-menu{display:grid;gap:6px}.t4tw-hierarchy{min-height:0;display:grid;grid-template-columns:minmax(150px,.7fr) minmax(170px,.85fr) minmax(260px,1.45fr);gap:8px;overflow:hidden}.t4tw-hierarchy-column{min-height:0;display:flex;flex-direction:column;border:1px solid var(--line);border-radius:12px;background:#081018;overflow:hidden}.t4tw-hierarchy-head{padding:8px 10px;border-bottom:1px solid var(--line);font-size:9px;font-weight:900;letter-spacing:1.1px;color:#8ba5b3;text-transform:uppercase}.t4tw-hierarchy-list{min-height:0;overflow:auto;display:grid;gap:5px;align-content:start;padding:7px}.t4tw-hierarchy-node{border:1px solid #1c303c;background:#0d1820;color:#dcebf2;border-radius:9px;padding:9px 10px;text-align:left;cursor:pointer}.t4tw-hierarchy-node:hover{border-color:#3e6f87}.t4tw-hierarchy-node.active{border-color:var(--cyan);background:#102733;color:#f4fbff}.t4tw-hierarchy-node:disabled{opacity:.52;cursor:not-allowed;border-style:dashed}.t4tw-hierarchy-node b{display:block;font-size:12px}.t4tw-hierarchy-node small{display:block;color:var(--muted);font-size:9px;margin-top:3px}.t4tw-hierarchy-feature{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;align-items:center}.t4tw-hierarchy-kind{font-size:8px;border:1px solid #345365;border-radius:99px;padding:4px 6px;color:#9fcce2;white-space:nowrap}.t4tw-hierarchy-breadcrumb{font-size:10px;color:#9db1bc;padding:1px 2px 5px}.t4tw-hierarchy-empty{padding:12px;color:var(--muted);font-size:11px;text-align:center}.t4tw-settings-heading{font-size:12px;font-weight:900;letter-spacing:1px;text-transform:uppercase;color:var(--cyan);padding:9px 4px 2px}.t4tw-settings-group{font-size:10px;font-weight:900;letter-spacing:.8px;text-transform:uppercase;color:#9db1bc;padding:8px 8px 2px;border-top:1px solid #172732}.t4tw-route-title{font-size:11px;font-weight:900;color:#d8e8ef;padding:7px 10px 3px;border-left:2px solid #29495b;margin:2px 0 1px}.t4tw-route-title small{display:block;color:var(--muted);font:9px ui-monospace,SFMono-Regular,Menlo,monospace;margin-top:2px}.t4tw-ungrouped{margin-top:8px;border-top:1px dashed #29404e}.t4tw-settings-item{display:grid;grid-template-columns:minmax(180px,1fr) auto;gap:10px;align-items:center;border:1px solid var(--line);background:linear-gradient(145deg,#101a22,#0a1117);border-radius:11px;padding:10px 12px;text-align:left;color:#eef7fc}.t4tw-settings-item:not(:disabled){cursor:pointer}.t4tw-settings-item:not(:disabled):hover{border-color:#3b718c}.t4tw-settings-item:disabled{opacity:.55}.t4tw-settings-item-title{font-size:13px;font-weight:900}.t4tw-settings-item-meta{font-size:9px;color:var(--muted);margin-top:3px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.t4tw-settings-kind{font-size:9px;border:1px solid #345365;border-radius:99px;padding:4px 7px;color:#9fcce2;white-space:nowrap}.t4tw-settings-modal{z-index:10030}.t4tw-settings-dialog{width:min(900px,97vw)}.t4tw-settings-body{display:grid;gap:8px;margin-top:12px}.t4tw-settings-panel{border:1px solid var(--line);border-radius:12px;background:#091119;padding:10px;display:grid;gap:7px}.t4tw-settings-panel>.t4tw-settings-panel-title{font-size:11px;font-weight:900;color:#cfe7f2;letter-spacing:.5px}.t4tw-settings-field{display:grid;grid-template-columns:minmax(170px,1fr) minmax(120px,auto);gap:10px;align-items:center;border:1px solid #1e303b;border-radius:9px;background:#071018;padding:9px 10px;color:#eaf4f8;text-align:left}.t4tw-settings-field.editable{cursor:pointer}.t4tw-settings-field.editable:hover{border-color:#3f7a98}.t4tw-settings-field.blocked,.t4tw-row.blocked,.t4tw-arrayrow.blocked{opacity:.58;border-style:dashed;cursor:not-allowed}.t4tw-condition.unsupported{color:#ff9ca7}.t4tw-condition.disabled{color:#ffe07a}.t4tw-settings-field.pending{border-color:#6f5b24}.t4tw-settings-field-label{font-weight:800;font-size:12px}.t4tw-settings-field-key{font:9px ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--muted);margin-top:3px}.t4tw-settings-field-value{text-align:right;font-weight:900;font-size:13px}.t4tw-settings-field-value small{display:block;color:var(--muted);font-size:8px;font-weight:700;margin-top:2px}.t4tw-settings-text{color:#9db1bc;font-size:11px;padding:5px 7px;line-height:1.35}.t4tw-settings-text.attention{color:#ff9ca7}.t4tw-condition{font:9px ui-monospace,SFMono-Regular,Menlo,monospace;color:#d6b966;margin-top:4px}.t4tw-settings-command{width:100%;border:1px dashed #65434a;background:#1b1013;color:#a77c82;border-radius:9px;padding:9px;text-align:left}.t4tw-settings-help{color:var(--muted);font-size:10px;margin-top:3px}.t4tw-settings-breadcrumb{font-size:10px;color:var(--muted);margin-top:2px}.t4tw-enum-options{font-size:8px;color:#708693;margin-top:3px;max-width:360px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.t4tw-bit-control{min-width:120px;border:1px solid #345365;background:#0c1820;color:#eaf4f8;border-radius:8px;padding:7px 9px;font-weight:800}.t4tw-bit-toggle{cursor:pointer}.t4tw-bit-toggle.pending,.t4tw-bit-control.pending{border-color:#8a6f27;background:#201b0d}.t4tw-settings-footer{position:sticky;bottom:-16px;margin:12px -16px -16px;padding:10px 16px;background:#0b141cf2;border-top:1px solid #315064;display:flex;gap:10px;align-items:center;justify-content:space-between;z-index:2}.t4tw-settings-footer-status{color:#9db1bc;font-size:11px}.t4tw-settings-footer .t4tw-realwrite{margin:0;width:auto;min-width:210px}
    /* TUNER_UI_SIDEQUEST_PARAMETERS_V1 */
    .t4tw-parameters-shell{min-height:0;display:grid;grid-template-columns:minmax(0,1fr);gap:8px;overflow:hidden}
    .t4tw-system-rail{display:none;min-height:0;border:1px solid var(--line);border-radius:12px;background:#081018;overflow:hidden}
    .t4tw-system-rail-head{padding:8px 10px;border-bottom:1px solid var(--line);font-size:9px;font-weight:900;letter-spacing:1px;color:#7892a0;text-transform:uppercase}
    .t4tw-system-rail-list{min-height:0;overflow:auto;display:grid;align-content:start;gap:5px;padding:7px}
    .t4tw-system-rail-item{border:1px solid #1c303c;background:#0d1820;color:#cfe2eb;border-radius:9px;padding:9px 10px;text-align:left;font-size:11px;font-weight:850;cursor:pointer}
    .t4tw-system-rail-item.active{border-color:var(--cyan);background:#102733;color:#fff}
    .t4tw-parameters-main{min-width:0;min-height:0;display:flex;flex-direction:column;gap:8px;overflow:hidden}
    .t4tw-parameter-feature-head{display:flex;gap:10px;align-items:flex-end;justify-content:space-between;min-width:0;padding:2px 2px 0}
    .t4tw-parameter-feature-title{min-width:0;font-size:16px;font-weight:900;color:#edf7fb;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .t4tw-parameter-feature-path{font-size:9px;color:#8098a5;margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .t4tw-inline-parameters{min-height:0;overflow:auto;display:grid;gap:8px;align-content:start;padding:1px}
    .t4tw-inline-parameters>.t4tw-settings-panel{grid-template-columns:repeat(2,minmax(0,1fr));align-items:start}
    .t4tw-inline-parameters>.t4tw-settings-panel>.t4tw-settings-panel-title,
    .t4tw-inline-parameters>.t4tw-settings-panel>.t4tw-settings-text,
    .t4tw-inline-parameters>.t4tw-settings-panel>.t4tw-settings-command,
    .t4tw-inline-parameters>.t4tw-settings-panel>div:not(.t4tw-settings-field){grid-column:1/-1}
    .t4tw-inline-actions{display:flex;gap:8px;align-items:center;justify-content:flex-end;border-top:1px solid #172732;padding-top:8px}
    .t4tw-inline-pending{margin-right:auto;color:#8ca3af;font-size:10px}
    @media(min-width:900px) and (min-height:600px) and (min-aspect-ratio:4/3){
      .t4tw-parameters-shell{grid-template-columns:168px minmax(0,1fr)}
      .t4tw-system-rail{display:flex;flex-direction:column}
      .t4tw-parameters-main .t4tw-hierarchy-selectors{grid-template-columns:18px minmax(170px,1fr) 18px minmax(220px,1.35fr)}
      .t4tw-hierarchy-select-wrap[data-hierarchy-level="system"]{display:none}
      .t4tw-hierarchy-chevron[data-hierarchy-from="system"]{display:block}
    }
    @media(max-width:720px){
      .t4tw-inline-parameters>.t4tw-settings-panel{grid-template-columns:1fr}
      .t4tw-parameter-feature-title{font-size:14px}
      .t4tw-settings-field{grid-template-columns:minmax(120px,1fr) minmax(96px,.65fr);padding:8px}
      .t4tw-settings-field-key,.t4tw-enum-options{display:none}
      .t4tw-inline-actions{position:sticky;bottom:0;background:#081018f2;padding:7px 0 1px}
    }
    /* TUNER_UI_SIDEQUEST_TABLES_V1 */
    .t4tw-expert-mode{opacity:.62}
    .t4tw-list.t4tw-fullarea{flex:1;overflow:hidden;align-content:stretch}
    .t4tw-table-shell{height:100%;min-height:0;display:grid;grid-template-columns:minmax(0,1fr);gap:8px;overflow:hidden}
    .t4tw-table-main{min-width:0;min-height:0;display:flex;flex-direction:column;gap:7px;overflow:hidden}
    .t4tw-table-head{display:flex;gap:8px;align-items:center;min-width:0}
    .t4tw-table-title-block{min-width:0;flex:1}
    .t4tw-table-title{font-size:15px;font-weight:900;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .t4tw-table-sub{font-size:9px;color:#819aa7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}
    .t4tw-table-view-controls{display:flex;gap:5px;align-items:center;flex-wrap:wrap}
    .t4tw-table-viewport{min-height:180px;flex:1;overflow:auto;border:1px solid var(--line);border-radius:11px;background:#061018;padding:5px;overscroll-behavior:contain}
    .t4tw-table-grid{display:grid;gap:1px;align-content:start;min-width:max-content}
    .t4tw-table-grid .t4tw-cell{min-width:var(--t4tw-inline-cell,24px);width:var(--t4tw-inline-cell,24px);padding:5px 2px;border-radius:3px;font-size:8px;line-height:1.05;overflow:hidden}
    .t4tw-table-grid .t4tw-cell small{display:none}
    .t4tw-table-grid .t4tw-cell.heat{background:hsl(var(--t4tw-heat-hue) 82% 45%);border-color:hsl(var(--t4tw-heat-hue) 70% 31%);color:var(--t4tw-heat-fg,#071018);text-shadow:0 1px 1px #ffffff22}.t4tw-table-grid .t4tw-cell.sel-top{border-top:2px solid #20c6ff}.t4tw-table-grid .t4tw-cell.sel-bottom{border-bottom:2px solid #20c6ff}.t4tw-table-grid .t4tw-cell.sel-left{border-left:2px solid #20c6ff}.t4tw-table-grid .t4tw-cell.sel-right{border-right:2px solid #20c6ff}.t4tw-table-grid .t4tw-cell.multiselected{box-shadow:inset 0 0 0 999px #0a94c820}
    .t4tw-table-grid .t4tw-cell.axis{min-width:52px;width:auto;font-size:8px;position:sticky;z-index:2}
    .t4tw-table-grid .t4tw-cell.axis:first-child{left:0;top:0;z-index:4}
    .t4tw-table-grid .t4tw-cell.axis[data-axis-side="row"]{left:0;z-index:3}
    .t4tw-table-grid .t4tw-cell.axis[data-axis-side="column"]{top:0;z-index:3}
    .t4tw-table-editbar{display:flex;gap:5px;align-items:center;flex-wrap:wrap;border-top:1px solid #172732;padding-top:7px}
    .t4tw-table-selection{font-size:10px;color:#93a9b4;min-width:92px}
    .t4tw-table-value-input{width:86px;flex:0 0 86px;background:#061018;border:1px solid var(--line);border-radius:8px;padding:7px;color:#fff;font-weight:800}
    .t4tw-table-status{font-size:9px;color:#819aa7;min-height:12px}
    @media(min-width:900px) and (min-height:600px) and (min-aspect-ratio:4/3){
      .t4tw-table-shell{grid-template-columns:168px minmax(0,1fr)}
      .t4tw-table-shell>.t4tw-system-rail{display:flex;flex-direction:column}
      .t4tw-table-main .t4tw-hierarchy-selectors{grid-template-columns:18px minmax(170px,1fr) 18px minmax(220px,1.35fr)}
      .t4tw-table-main .t4tw-hierarchy-select-wrap[data-hierarchy-level="system"]{display:none}
    }
    @media(max-width:720px){
      .t4tw-table-head{align-items:flex-start}
      .t4tw-table-view-controls{justify-content:flex-end}
      .t4tw-table-grid .t4tw-cell{font-size:7px;padding:4px 1px}
      .t4tw-table-grid .t4tw-cell.axis{min-width:42px}
      .t4tw-table-editbar{gap:4px}
      .t4tw-table-selection{min-width:70px}
    }
    /* TUNER_UI_SIDEQUEST_CURVES_V1 */
    .t4tw-curve-shell{height:100%;min-height:0;display:grid;grid-template-columns:minmax(0,1fr);gap:8px;overflow:hidden}
    .t4tw-curve-main{min-width:0;min-height:0;display:flex;flex-direction:column;gap:7px;overflow:hidden}
    .t4tw-curve-head{display:flex;gap:8px;align-items:center;min-width:0}
    .t4tw-curve-title-block{min-width:0;flex:1}
    .t4tw-curve-title{font-size:15px;font-weight:900;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .t4tw-curve-sub{font-size:9px;color:#819aa7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}
    .t4tw-curve-mode{display:flex;gap:4px}
    .t4tw-curve-workspace{flex:1;min-height:0;display:grid;grid-template-columns:minmax(0,1fr) 220px;gap:8px}
    .t4tw-curve-plot-wrap{min-height:240px;border:1px solid var(--line);border-radius:11px;background:#061018;overflow:hidden;position:relative}
    .t4tw-curve-svg{width:100%;height:100%;display:block;min-height:240px;touch-action:none}
    .t4tw-curve-manager{min-height:0;border:1px solid var(--line);border-radius:11px;background:#081018;display:flex;flex-direction:column;overflow:hidden}
    .t4tw-curve-manager-head{padding:8px 10px;border-bottom:1px solid var(--line);font-size:10px;font-weight:900;color:#cfe7f2}
    .t4tw-curve-list{min-height:0;overflow:auto;display:grid;align-content:start;gap:5px;padding:7px}
    .t4tw-curve-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:6px;align-items:center;border:1px solid #1d303b;border-radius:8px;padding:7px;background:#0b151d}.t4tw-curve-row{cursor:pointer}.t4tw-curve-row:focus{outline:1px solid #20c6ff}.t4tw-curve-mini.active{border-color:#20c6ff;color:#31d1ff;background:#0c3144}
    .t4tw-curve-row.active{border-color:var(--cyan);background:#102733}
    .t4tw-curve-row-name{min-width:0;font-size:10px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .t4tw-curve-row-meta{font-size:8px;color:#758d99;margin-top:2px}
    .t4tw-curve-row-actions{display:flex;gap:3px}
    .t4tw-curve-mini{border:1px solid #2b4655;background:#0c1820;color:#dcebf2;border-radius:6px;padding:4px 6px;font-size:8px;font-weight:800}
    .t4tw-curve-add{display:block;padding:7px;border-top:1px solid var(--line)}.t4tw-curve-add>summary{width:100%;justify-content:center;border-style:dashed;color:#24c7ff}
    .t4tw-curve-add select{min-width:150px;width:100%;background:#061018;border:1px solid var(--line);color:#fff;border-radius:7px;padding:7px;font-size:9px;margin-bottom:5px}
    .t4tw-curve-editbar{display:flex;gap:5px;align-items:center;flex-wrap:wrap;border-top:1px solid #172732;padding-top:7px}
    .t4tw-curve-value-input{width:86px;flex:0 0 86px;background:#061018;border:1px solid var(--line);border-radius:8px;padding:7px;color:#fff;font-weight:800}
    .t4tw-curve-status{font-size:9px;color:#819aa7;min-height:12px}
    @media(min-width:900px) and (min-height:600px) and (min-aspect-ratio:4/3){
      .t4tw-curve-shell{grid-template-columns:168px minmax(0,1fr)}
      .t4tw-curve-shell>.t4tw-system-rail{display:flex;flex-direction:column}
      .t4tw-curve-main .t4tw-hierarchy-selectors{grid-template-columns:18px minmax(170px,1fr) 18px minmax(220px,1.35fr)}
      .t4tw-curve-main .t4tw-hierarchy-select-wrap[data-hierarchy-level="system"]{display:none}
    }
    @media(max-width:899px) and (min-height:600px){
      .t4tw-curve-workspace{grid-template-columns:1fr;grid-template-rows:minmax(260px,1fr) auto}
      .t4tw-curve-manager{max-height:230px}
      .t4tw-curve-list{grid-template-columns:1fr}
    }
    @media(max-width:520px){
      .t4tw-curve-list{grid-template-columns:1fr}
      .t4tw-curve-head{align-items:flex-start}
      .t4tw-curve-editbar{gap:4px}
      .t4tw-curve-svg{min-height:220px}
    }
    /* TUNER_UI_SIDEQUEST_RESPONSIVE_HIERARCHY_V1 */
    .t4tw-hierarchy-selectors{display:grid;grid-template-columns:minmax(150px,.8fr) 18px minmax(170px,1fr) 18px minmax(220px,1.35fr);gap:7px;align-items:center}
    .t4tw-hierarchy-select-wrap{display:grid;gap:4px;min-width:0}
    .t4tw-hierarchy-select-label{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
    .t4tw-hierarchy-select{width:100%;min-width:0;background:#0b151d;border:1px solid #2a4454;color:#edf7fb;border-radius:10px;padding:9px 34px 9px 11px;font-size:12px;font-weight:800}
    .t4tw-hierarchy-select:focus{outline:1px solid var(--cyan);border-color:var(--cyan)}
    .t4tw-hierarchy-chevron{align-self:center;text-align:center;color:#87aabc;font-size:21px;font-weight:700}
    @media(max-width:900px){.t4tw-hierarchy-selectors{grid-template-columns:minmax(130px,.8fr) 14px minmax(145px,1fr) 14px minmax(180px,1.2fr);gap:5px}.t4tw-hierarchy-chevron{font-size:18px}}
    @media(max-width:720px){.t4tw-hierarchy-selectors{grid-template-columns:1fr;gap:5px}.t4tw-hierarchy-chevron{display:none}.t4tw-hierarchy{grid-template-columns:1fr}.t4tw-hierarchy-column{max-height:28vh}.t4tw-row{grid-template-columns:34px minmax(120px,1fr) minmax(110px,.6fr)}.t4tw-range{display:none}.t4tw-meta{min-width:100%}.t4tw-grid{grid-template-columns:1fr}.t4tw-change{grid-template-columns:1fr auto}.t4tw-regionmeta{min-width:100%}}

    /* TUNER_UI_VISUAL_PARITY_V1 — locked tablet/phone reference family */
    body.t4tw-tuner-active>#app>header,
    body.t4tw-tuner-active>#app>#shellTabs,
    body.t4tw-tuner-active>#app>#tabs,
    body.t4tw-tuner-active>#app>#diagnosticsTabs,
    body.t4tw-tuner-active>#app>footer{display:none!important}
    body.t4tw-tuner-active>#app{height:100vh;overflow:hidden}
    body.t4tw-tuner-active main{height:100%;min-height:0;padding:0!important}
    #page-tuning{height:100%;min-height:0;overflow:hidden;background:radial-gradient(circle at 70% 0,#0b1c27 0,#071018 42%,#050b10 100%)}
    #page-tuning.active{display:flex!important;flex-direction:column}
    .t4tw-topchrome{flex:0 0 auto;border-bottom:1px solid #17313f;background:#071018f2;backdrop-filter:blur(10px)}
    .t4tw-appbar{min-height:68px;display:grid;grid-template-columns:minmax(190px,auto) minmax(360px,1fr) auto;gap:16px;align-items:center;padding:8px 18px}
    .t4tw-brand{font-size:24px;font-weight:900;letter-spacing:-.6px;font-style:italic;white-space:nowrap;color:#f5fbff}.t4tw-brand b{color:#ef2342;font-style:normal}
    .t4tw-appnav{display:grid;grid-template-columns:repeat(4,minmax(86px,1fr));gap:6px;max-width:620px}
    .t4tw-appnav-btn{display:flex;gap:8px;align-items:center;justify-content:center;border:0;border-radius:10px;background:transparent;color:#c8d9e3;padding:9px 12px;font-size:13px;font-weight:800}
    .t4tw-appnav-btn svg{width:20px;height:20px;stroke:currentColor;fill:none;stroke-width:1.8}
    .t4tw-appnav-btn.active{background:#0b3655;color:#13b9ff}
    .t4tw-appstatus{display:flex;gap:9px;align-items:center;justify-content:flex-end;min-width:0}
    .t4tw-top-action{height:40px;display:flex;align-items:center;gap:7px;border:1px solid #2a4757;border-radius:9px;background:#09151d;color:#d9e8ef;padding:0 12px;font-weight:850;white-space:nowrap}
    .t4tw-top-action.ini{border-color:#05a845;color:#48ef83}.t4tw-top-action.ini.amber{border-color:#8f6c1d;color:#ffd45b}.t4tw-top-action.ini.red{border-color:#8f3f47;color:#ff9da8}
    .t4tw-top-action.burn{border-color:#a86800;color:#ffad2f;background:#1d1407}.t4tw-top-action.burn.clean{opacity:.58;filter:saturate(.45)}
    .t4tw-ecu-state{display:flex;gap:8px;align-items:center;min-width:0;font-size:12px}.t4tw-ecu-dot{width:12px;height:12px;border-radius:50%;background:#4e6572;box-shadow:0 0 0 3px #4e657218}.t4tw-ecu-state.connected .t4tw-ecu-dot{background:#00e676;box-shadow:0 0 10px #00e67680}.t4tw-ecu-copy{min-width:0}.t4tw-ecu-copy b,.t4tw-ecu-copy small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.t4tw-ecu-copy small{font-size:9px;color:#8aa1ad;margin-top:2px}
    .t4tw-profile-chip{max-width:190px;color:#bce5f6}.t4tw-profile-chip span:first-child{max-width:150px;overflow:hidden;text-overflow:ellipsis}.t4tw-gear{width:40px;padding:0;justify-content:center;font-size:20px}
    .t4tw-shell{flex:1;min-height:0;gap:0;padding:10px 12px 0}
    .t4tw-internal-state{display:none!important}
    .t4tw-summary{display:none!important}
    .t4tw-list{flex:1;min-height:0;overflow:hidden;display:grid;gap:0}
    .t4tw-nav-row{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(330px,.85fr);gap:14px;align-items:center;margin-bottom:8px}
    .t4tw-content-modes{display:grid;grid-template-columns:repeat(3,minmax(92px,1fr));border:1px solid #294654;border-radius:9px;overflow:hidden;background:#07121a}
    .t4tw-content-mode{min-height:42px;border:0;border-right:1px solid #294654;background:transparent;color:#c5d6df;font-size:12px;font-weight:850;display:flex;gap:8px;align-items:center;justify-content:center}.t4tw-content-mode:last-child{border-right:0}.t4tw-content-mode.active{background:linear-gradient(180deg,#0c68b6,#075092);color:white;box-shadow:inset 0 0 0 1px #15baff}.t4tw-content-mode svg{width:18px;height:18px;stroke:currentColor;fill:none;stroke-width:1.8}
    .t4tw-system-rail{border:0;border-right:1px solid #19313e;border-radius:0;background:#07121a;margin:-10px 0 0 -12px}
    .t4tw-system-rail-head{display:none}.t4tw-system-rail-list{padding:10px 6px;gap:1px}
    .t4tw-system-rail-item{border:0;border-left:3px solid transparent;border-radius:0;background:transparent;color:#c6d8e1;padding:11px 10px;display:flex;align-items:center;gap:9px;font-size:12px}
    .t4tw-system-rail-item:hover{background:#0c1d27}.t4tw-system-rail-item.active{border-color:#11aef2;background:linear-gradient(90deg,#0b4778,#0d2a40);color:#fff}
    .t4tw-system-rail-icon{width:22px;height:22px;display:grid;place-items:center;color:#a9d7ef}.t4tw-system-rail-icon svg{width:21px;height:21px;stroke:currentColor;fill:none;stroke-width:1.7}.t4tw-system-rail-item.active .t4tw-system-rail-icon{color:#1fc2ff}
    .t4tw-hierarchy-select{min-height:42px;border-radius:8px;background:#07131b;border-color:#284754;font-size:12px;padding:8px 34px 8px 12px}
    .t4tw-hierarchy-chevron{color:#9cc7dc;font-size:24px}
    .t4tw-parameter-feature-head{display:none}
    .t4tw-inline-actions{display:none!important}
    .t4tw-inline-parameters{padding:0 1px 4px;grid-template-columns:1fr;gap:10px}
    .t4tw-inline-parameters>.t4tw-settings-root{border:0;background:transparent;padding:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
    .t4tw-inline-parameters>.t4tw-settings-root>.t4tw-settings-panel-title{display:none}
    .t4tw-settings-root>.t4tw-settings-panel-wrap{display:contents}
    .t4tw-settings-root>.t4tw-settings-field{min-height:68px}
    .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){border-color:#244552;background:linear-gradient(180deg,#0b1c25,#07131a);padding:0 10px 10px;gap:5px;overflow:hidden}
    .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel-title{margin:0 -10px 4px;padding:10px 12px;border-bottom:1px solid #1d3946;background:#0c222d;color:#21c8f6;font-size:14px;text-transform:none;letter-spacing:0}
    @media(min-width:600px){
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:repeat(2,minmax(0,1fr))}
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel-title,
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-text,
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-command,
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel{grid-column:1/-1}
    }
    .t4tw-inline-parameters .t4tw-settings-field{border:0;border-bottom:1px solid #18313d;border-radius:0;background:transparent;padding:7px 3px;grid-template-columns:minmax(130px,1fr) minmax(100px,.62fr);min-height:42px}
    .t4tw-inline-parameters .t4tw-settings-field:last-child{border-bottom:0}
    .t4tw-inline-parameters .t4tw-settings-field-key,.t4tw-inline-parameters .t4tw-enum-options,.t4tw-inline-parameters .t4tw-settings-field-value>small{display:none!important}
    .t4tw-inline-parameters .t4tw-settings-field-value{border:0;background:transparent;padding:0;min-height:20px;font-size:12px}
    .t4tw-param-input-wrap{display:grid;grid-template-columns:minmax(70px,1fr) auto;gap:7px;align-items:center}
    .t4tw-param-input{width:100%;box-sizing:border-box;border:1px solid #31515f;border-radius:6px;background:#07121a;color:#f3f9fc;padding:7px 9px;font:800 12px system-ui,sans-serif}
    .t4tw-param-input:focus{outline:1px solid #18bdf4;border-color:#18bdf4}.t4tw-param-input[disabled]{opacity:.55}
    .t4tw-param-unit{font-size:10px;color:#b4c7d0;min-width:28px}
    .t4tw-inline-parameters .t4tw-bit-control{width:100%;min-width:0;padding:7px 9px}
    .t4tw-inline-parameters .t4tw-bit-toggle{justify-self:end;position:relative;min-width:62px;border-radius:18px;padding:6px 9px 6px 29px;text-align:right;background:#142733}
    .t4tw-inline-parameters .t4tw-bit-toggle:before{content:'';position:absolute;width:18px;height:18px;border-radius:50%;left:5px;top:50%;transform:translateY(-50%);background:#9aafbb;transition:.15s}
    .t4tw-inline-parameters .t4tw-bit-toggle.on{background:#0879c6;color:#fff;border-color:#18bdf4}.t4tw-inline-parameters .t4tw-bit-toggle.on:before{left:calc(100% - 23px);background:#fff}
    .t4tw-settings-panel-title{display:flex;align-items:center;gap:8px}.t4tw-settings-panel-icon{font-size:17px;color:#25caf7;min-width:20px;text-align:center}
    .t4tw-inline-parameters .t4tw-condition{font-size:8px}
    .t4tw-table-shell,.t4tw-curve-shell,.t4tw-parameters-shell{gap:10px}
    .t4tw-table-head,.t4tw-curve-head{min-height:42px}
    .t4tw-table-title,.t4tw-curve-title{color:#1bc5f5}
    .t4tw-table-viewport{border-color:#244552;background:#061018;padding:4px}
    .t4tw-table-editbar,.t4tw-curve-editbar{border:1px solid #213e4c;border-radius:9px;padding:8px;background:#07131a}
    .t4tw-table-status,.t4tw-curve-status{display:none}
    .t4tw-menu{position:relative}.t4tw-menu>summary{list-style:none;cursor:pointer}.t4tw-menu>summary::-webkit-details-marker{display:none}.t4tw-menu-panel{position:absolute;right:0;bottom:calc(100% + 6px);z-index:25;min-width:170px;display:grid;gap:4px;padding:6px;border:1px solid #31515f;border-radius:9px;background:#07131af2;box-shadow:0 12px 30px #0008}.t4tw-menu-panel button{width:100%;text-align:left}
    .t4tw-curve-manager{border-color:#244552;background:#07131a}.t4tw-curve-manager-head{font-size:13px;padding:10px 12px}
    .t4tw-curve-row{border-width:0 0 0 3px;border-radius:0;background:transparent;padding:8px 7px;border-bottom:1px solid #18313d}.t4tw-curve-row.active{background:#0b2633}
    .t4tw-curve-plot-wrap{border-color:#244552}
    .t4tw-telemetry{flex:0 0 auto;display:grid;grid-template-columns:repeat(10,minmax(72px,1fr));gap:6px;padding:8px 0 10px;border-top:1px solid #18313d;background:#071018}
    .t4tw-telemetry-slot{min-width:0;border:1px solid #254554;border-radius:7px;background:#07131a;padding:6px 8px}.t4tw-telemetry-label{font-size:9px;color:#22bff0;font-weight:800}.t4tw-telemetry-value{display:flex;gap:4px;align-items:baseline;margin-top:2px;min-width:0}.t4tw-telemetry-value b{font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.t4tw-telemetry-value small{font-size:9px;color:#a9bdc7}
    @media(min-width:900px) and (min-height:600px) and (min-aspect-ratio:4/3){.t4tw-parameters-shell,.t4tw-table-shell,.t4tw-curve-shell{grid-template-columns:180px minmax(0,1fr)}}
    @media(max-width:1100px){
      .t4tw-appbar{grid-template-columns:1fr auto;gap:8px;padding:7px 12px}.t4tw-appnav{grid-column:1/-1;grid-row:2;max-width:none}.t4tw-appstatus{grid-column:2}.t4tw-brand{font-size:22px}.t4tw-appnav-btn{padding:7px 8px}
      .t4tw-nav-row{grid-template-columns:1fr;gap:7px}.t4tw-content-modes{order:2}.t4tw-hierarchy-selectors{order:1}
      .t4tw-telemetry{grid-template-columns:repeat(6,minmax(70px,1fr))}
    }
    @media(min-width:700px) and (max-width:1100px) and (min-height:760px){
      .t4tw-appbar{grid-template-columns:180px minmax(0,1fr);grid-template-rows:auto auto}
      .t4tw-appnav{grid-column:2;grid-row:1;max-width:none}
      .t4tw-appstatus{grid-column:1/-1;grid-row:2;justify-content:flex-start;padding-top:2px}
    }
    @media(min-width:700px) and (max-width:1100px) and (max-height:650px){
      .t4tw-appbar{grid-template-columns:180px minmax(360px,1fr) auto;grid-template-rows:auto}
      .t4tw-appnav{grid-column:2;grid-row:1;max-width:none}
      .t4tw-appstatus{grid-column:3;grid-row:1}
      .t4tw-brand{font-size:20px}
      .t4tw-nav-row{grid-template-columns:minmax(0,1.2fr) minmax(300px,.8fr)}
    }
    @media(max-width:899px) and (min-height:700px){.t4tw-inline-parameters>.t4tw-settings-root{grid-template-columns:1fr}}
    @media(max-width:720px){
      .t4tw-shell{padding:8px 8px 0}.t4tw-appbar{padding:7px 9px}.t4tw-brand{font-size:20px}.t4tw-appstatus{gap:6px}.t4tw-top-action{height:36px;padding:0 9px;font-size:11px}.t4tw-ecu-copy small{display:none}.t4tw-appnav-btn{font-size:11px;gap:5px}.t4tw-appnav-btn svg{width:18px;height:18px}
      .t4tw-content-mode{min-height:40px}.t4tw-hierarchy-selectors{grid-template-columns:minmax(0,1fr) 12px minmax(0,1fr) 12px minmax(0,1fr);gap:4px}.t4tw-hierarchy-chevron{display:block;font-size:18px}
      .t4tw-inline-parameters>.t4tw-settings-root{grid-template-columns:1fr}.t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){padding:0 8px 8px}.t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel-title{margin:0 -8px 3px;padding:9px 10px;font-size:13px}
      .t4tw-telemetry{grid-template-columns:repeat(5,minmax(62px,1fr));gap:4px;padding-top:6px}.t4tw-telemetry-slot:nth-child(n+7){display:none}
    }
    @media(max-width:520px){
      .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:1fr}
      .t4tw-appbar{grid-template-columns:1fr auto}.t4tw-brand{font-size:18px}.t4tw-appstatus .t4tw-ecu-copy{display:none}.t4tw-top-action.ini,.t4tw-top-action.burn{padding:0 8px}.t4tw-profile-chip{display:none}.t4tw-gear{width:36px}
      .t4tw-content-mode{font-size:11px}
      .t4tw-table-head,.t4tw-curve-head{align-items:flex-start;flex-wrap:wrap}.t4tw-table-view-controls,.t4tw-curve-mode{margin-left:auto}
      .t4tw-telemetry{grid-template-columns:repeat(5,minmax(56px,1fr))}
    }
  `;
  document.head.appendChild(style);

  const page = document.createElement('section');
  page.className = 'page';
  page.id = 'page-tuning';
  page.innerHTML = `
    <div class="t4tw-topchrome">
      <div class="t4tw-appbar">
        <div class="t4tw-brand">EpicDash <b>JZ</b></div>
        <nav class="t4tw-appnav" aria-label="Application navigation">
          <button class="t4tw-appnav-btn" type="button" data-shell-target="dash"><svg viewBox="0 0 24 24"><path d="M4 15a8 8 0 1 1 16 0"/><path d="M12 12l4-4"/><path d="M7 18h10"/></svg><span>Dash</span></button>
          <button class="t4tw-appnav-btn active" type="button" data-shell-target="tuner"><svg viewBox="0 0 24 24"><path d="M14 6l4-4 4 4-4 4"/><path d="M13 7L4 16v4h4l9-9"/></svg><span>Tuner</span></button>
          <button class="t4tw-appnav-btn" type="button" data-shell-target="logging"><svg viewBox="0 0 24 24"><path d="M5 20V11M12 20V4M19 20v-7"/></svg><span>Logging</span></button>
          <button class="t4tw-appnav-btn" type="button" data-shell-target="diagnostics"><svg viewBox="0 0 24 24"><path d="M2 13h4l2-7 4 14 3-9 2 4h5"/></svg><span>Diagnostics</span></button>
        </nav>
        <div class="t4tw-appstatus">
          <button class="t4tw-top-action ini amber" id="t4twTopIni" type="button"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v5h5"/><path d="M9 12h6M9 16h6"/></svg><span id="t4twTopIniLabel">INI</span></button>
          <button class="t4tw-top-action t4tw-profile-chip" id="t4twTopProfile" type="button"><span id="t4twTopProfileLabel">mainController.ini</span><span aria-hidden="true">⌄</span></button>
          <button class="t4tw-top-action burn clean" id="t4twTopBurn" type="button"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2c1 4-2 5-1 8 1-1 3-2 4-4 3 3 4 6 4 9a8 8 0 1 1-16 0c0-4 2-7 6-10 0 3 0 5 2 6"/></svg><span id="t4twTopBurnLabel">Burn</span></button>
          <div class="t4tw-ecu-state" id="t4twTopEcu"><span class="t4tw-ecu-dot"></span><span class="t4tw-ecu-copy"><b>ECU Offline</b><small id="t4twTopEcuSub">Waiting for current tune</small></span></div>
          <button class="t4tw-top-action t4tw-gear" id="t4twTopGear" type="button" aria-label="Settings"><svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19 13.5v-3l-2-.7-.8-1.8.9-1.9-2.2-2.1-1.8.9-1.9-.8L10.5 2h-3L6.8 4.1l-1.9.8-1.8-.9L1 6.1 2 8l-.8 1.8-2 .7v3l2 .7L2 16l-1 1.9L3.1 20l1.8-.9 1.9.8.7 2.1h3l.7-2.1 1.9-.8 1.8.9 2.2-2.1-1-1.9.9-1.8z" transform="translate(2 0) scale(.83)"/></svg></button>
        </div>
      </div>
    </div>
    <div class="t4tw-shell">
      <div class="t4tw-internal-state" aria-hidden="true" hidden>
        <div id="t4twMeta"></div>
        <div id="t4twSummary"></div>
        <input id="t4twSearch" type="search" tabindex="-1">
        <button id="t4twChanges" type="button" tabindex="-1"><span id="t4twChangeCount">0</span></button>
        <button id="t4twImportIni" type="button" tabindex="-1"></button>
        <button id="t4twRefresh" type="button" tabindex="-1"></button>
        <button id="t4twReadEcu" type="button" tabindex="-1"></button>
        <button id="t4twSaveEcu" type="button" tabindex="-1"></button>
        <div id="t4twModes"></div>
        <div id="t4twFilters"></div>
      </div>
      <div class="t4tw-list" id="t4twList"><div class="t4tw-empty">Open this page after USB has completed the TuneSnapshot.</div></div>
      <div class="t4tw-telemetry" id="t4twTelemetry"></div>
    </div>
    <div class="t4tw-modal t4tw-settings-modal" id="t4twSettingsDialog" hidden>
      <div class="t4tw-dialog t4tw-settings-dialog" role="dialog" aria-modal="true" aria-labelledby="t4twSettingsTitle">
        <div class="t4tw-dialog-head"><div style="flex:1"><div class="t4tw-dialog-title" id="t4twSettingsTitle">INI Settings</div><div class="t4tw-settings-breadcrumb" id="t4twSettingsBreadcrumb"></div><div class="t4tw-settings-help" id="t4twSettingsHelp"></div></div><button class="t4tw-close" id="t4twSettingsClose" type="button">Close</button></div>
        <div class="t4tw-settings-body" id="t4twSettingsBody"></div>
        <div class="t4tw-settings-footer"><div class="t4tw-settings-footer-status" id="t4twSettingsPending">No pending edits.</div><button id="t4twSettingsWriteRam" class="t4tw-btn t4tw-realwrite" type="button" disabled>Write pending to ECU RAM</button></div>
      </div>
    </div>
    <div class="t4tw-modal" id="t4twEditor" hidden><div class="t4tw-dialog" role="dialog" aria-modal="true" aria-labelledby="t4twEditorTitle"><div class="t4tw-dialog-head"><div class="t4tw-dialog-title" id="t4twEditorTitle">Setting</div><button class="t4tw-close" id="t4twEditorClose" type="button">Close</button></div><div class="t4tw-grid"><div class="t4tw-stat"><small>Current ECU value</small><b id="t4twCurrent">—</b></div><div class="t4tw-stat"><small>Pending edit</small><b id="t4twRequested">—</b></div><div class="t4tw-stat"><small>ECU-effective value</small><b id="t4twEffective">—</b></div></div><div class="t4tw-inputrow"><input id="t4twRequestedInput" class="t4tw-input" inputmode="decimal"><button id="t4twApplyEdit" class="t4tw-btn" type="button">Apply edit</button></div><div class="t4tw-status" id="t4twEditorStatus">Edit locally, then write this pending value to ECU RAM. Nothing is burned until Burn.</div><button id="t4twApplyRam" class="t4tw-btn t4tw-realwrite" type="button" disabled>Write this edit to ECU RAM</button></div></div>
    <div class="t4tw-modal" id="t4twArrayEditor" hidden><div class="t4tw-dialog" role="dialog" aria-modal="true" aria-labelledby="t4twArrayTitle"><div class="t4tw-dialog-head"><div><div class="t4tw-dialog-title" id="t4twArrayTitle">Table / Curve</div><div class="sub" id="t4twArrayMeta">PENDING EDITS</div></div><button class="t4tw-close" id="t4twArrayClose" type="button">Close</button></div><div class="t4tw-regionbar" id="t4twRegionBar"><div class="t4tw-regionmeta" id="t4twSelectionMeta">0 cells selected</div><button id="t4twSelectRow" class="t4tw-btn" type="button">Select row</button><button id="t4twSelectColumn" class="t4tw-btn" type="button">Select column</button><button id="t4twSelectRange" class="t4tw-btn" type="button">Rectangle / range to next cell</button><button id="t4twSelectAll" class="t4tw-btn" type="button">Select all</button><button id="t4twSelectClear" class="t4tw-btn" type="button">Clear selection</button><select id="t4twRegionOperation" class="t4tw-regionselect" aria-label="Selection edit operation"><option value="set">Set to</option><option value="add">Add</option><option value="subtract">Subtract</option><option value="percent">Adjust %</option></select><input id="t4twRegionValue" class="t4tw-input t4tw-regionvalue" inputmode="decimal" placeholder="Value"><button id="t4twApplySelection" class="t4tw-btn" type="button">Apply to selection</button></div><div class="t4tw-arraygrid" id="t4twArrayGrid"></div><div class="t4tw-grid"><div class="t4tw-stat"><small>Current ECU value</small><b id="t4twArrayCurrent">—</b></div><div class="t4tw-stat"><small>Pending edit</small><b id="t4twArrayRequested">—</b></div><div class="t4tw-stat"><small>ECU-effective value</small><b id="t4twArrayEffective">—</b></div></div><div class="t4tw-inputrow"><input id="t4twArrayInput" class="t4tw-input" inputmode="decimal" disabled><button id="t4twArrayApplyEdit" class="t4tw-btn" type="button" disabled>Apply edit</button><button id="t4twArrayWrite" class="t4tw-btn t4tw-realwrite" type="button" hidden>Write this cell to ECU RAM</button></div><div class="t4tw-status" id="t4twArrayStatus">Select a cell and apply an edit.</div></div></div>
    <div class="t4tw-modal" id="t4twChangesModal" hidden><div class="t4tw-dialog" role="dialog" aria-modal="true"><div class="t4tw-dialog-head"><div><div class="t4tw-dialog-title">Pending changes</div><div class="sub">Bound to this exact ECU tune fingerprint</div></div><button class="t4tw-close" id="t4twChangesClose" type="button">Close</button></div><div class="t4tw-changes" id="t4twChangesList"></div><div class="t4tw-status" id="t4twChangesStatus">Ready.</div><div style="display:grid;gap:9px;margin-top:12px"><button id="t4twWriteAll" class="t4tw-btn t4tw-realwrite" style="width:100%;font-size:15px;padding:12px" type="button">WRITE ALL PENDING EDITS TO ECU RAM</button><button id="t4twSaveChanges" class="t4tw-btn t6" style="width:100%" type="button">Burn ECU</button><button id="t4twClearChanges" class="t4tw-btn" style="width:100%" type="button">Clear pending edits</button></div></div></div>`;
  main.appendChild(page);
  window.dispatchEvent(new CustomEvent('epicdash:tuner-ready'));

  const meta = document.getElementById('t4twMeta');
  const summary = document.getElementById('t4twSummary');
  const list = document.getElementById('t4twList');
  const search = document.getElementById('t4twSearch');
  const refresh = document.getElementById('t4twRefresh');
  const importIniButton = document.getElementById('t4twImportIni');
  const readEcuButton = document.getElementById('t4twReadEcu');
  const saveEcuButton = document.getElementById('t4twSaveEcu');
  const modes = document.getElementById('t4twModes');
  const filters = document.getElementById('t4twFilters');
  const changeCount = document.getElementById('t4twChangeCount');
  const changesButton = document.getElementById('t4twChanges');
  const settingsDialog = document.getElementById('t4twSettingsDialog');
  const settingsTitle = document.getElementById('t4twSettingsTitle');
  const settingsBreadcrumb = document.getElementById('t4twSettingsBreadcrumb');
  const settingsHelp = document.getElementById('t4twSettingsHelp');
  const settingsBody = document.getElementById('t4twSettingsBody');
  const settingsClose = document.getElementById('t4twSettingsClose');
  const settingsPending = document.getElementById('t4twSettingsPending');
  const settingsWriteRam = document.getElementById('t4twSettingsWriteRam');
  const editor = document.getElementById('t4twEditor');
  const editorTitle = document.getElementById('t4twEditorTitle');
  const editorClose = document.getElementById('t4twEditorClose');
  const currentNode = document.getElementById('t4twCurrent');
  const requestedNode = document.getElementById('t4twRequested');
  const effectiveNode = document.getElementById('t4twEffective');
  const requestedInput = document.getElementById('t4twRequestedInput');
  const applyEditButton = document.getElementById('t4twApplyEdit');
  const applyRamButton = document.getElementById('t4twApplyRam');
  const editorStatus = document.getElementById('t4twEditorStatus');
  const changesModal = document.getElementById('t4twChangesModal');
  const changesStatus = document.getElementById('t4twChangesStatus');
  const changesClose = document.getElementById('t4twChangesClose');
  const changesList = document.getElementById('t4twChangesList');
  const clearChanges = document.getElementById('t4twClearChanges');
  const arrayEditor = document.getElementById('t4twArrayEditor');
  const arrayTitle = document.getElementById('t4twArrayTitle');
  const arrayMeta = document.getElementById('t4twArrayMeta');
  const arrayClose = document.getElementById('t4twArrayClose');
  const arrayGrid = document.getElementById('t4twArrayGrid');
  const arrayCurrent = document.getElementById('t4twArrayCurrent');
  const arrayRequested = document.getElementById('t4twArrayRequested');
  const arrayEffective = document.getElementById('t4twArrayEffective');
  const arrayInput = document.getElementById('t4twArrayInput');
  const arrayApplyEdit = document.getElementById('t4twArrayApplyEdit');
  const arrayWrite = document.getElementById('t4twArrayWrite');
  const arrayStatus = document.getElementById('t4twArrayStatus');
  const selectionMeta = document.getElementById('t4twSelectionMeta');
  const selectRowButton = document.getElementById('t4twSelectRow');
  const selectColumnButton = document.getElementById('t4twSelectColumn');
  const selectRangeButton = document.getElementById('t4twSelectRange');
  const selectAllButton = document.getElementById('t4twSelectAll');
  const selectClearButton = document.getElementById('t4twSelectClear');
  const regionOperation = document.getElementById('t4twRegionOperation');
  const regionValue = document.getElementById('t4twRegionValue');
  const applySelectionButton = document.getElementById('t4twApplySelection');
  const writeAllButton = document.getElementById('t4twWriteAll');
  const saveChangesButton = document.getElementById('t4twSaveChanges');
  const topIniButton = document.getElementById('t4twTopIni');
  const topIniLabel = document.getElementById('t4twTopIniLabel');
  const topProfileButton = document.getElementById('t4twTopProfile');
  const topProfileLabel = document.getElementById('t4twTopProfileLabel');
  const topBurnButton = document.getElementById('t4twTopBurn');
  const topBurnLabel = document.getElementById('t4twTopBurnLabel');
  const topEcuState = document.getElementById('t4twTopEcu');
  const topEcuSub = document.getElementById('t4twTopEcuSub');
  const topGearButton = document.getElementById('t4twTopGear');
  const telemetryStrip = document.getElementById('t4twTelemetry');

  const favoritesKey = 'epicdash.t4.quickTuning.v1';
  let workspace = null;
  let workspaceIndex = null;
  let workspaceIndexProfileFingerprint = '';
  let groupedRoutesCache = new Map();
  let groupedRouteBlueprintCache = new Map();
  let tuningWriteStatus = null;
  let iniImportState = {phase:'idle', busy:false, message:'No INI import running'};
  let lastNativeWriteStateSignature = '';
  let activeFilter = 'all';
  let viewMode = 'settings';
  let hierarchySystem = '';
  let hierarchyCategory = '';
  let hierarchyFeatureId = '';
  let hierarchyTableId = '';
  let tableZoom = 1;
  let tableFitPending = true;
  let hierarchyCurveId = '';
  let curveMode = 'multi';
  let curveActiveId = '';
  let curveLoadedIds = [];
  let curveVisibility = new Set();
  let hierarchyCacheKey = '';
  let hierarchyCache = null;
  const HIERARCHY_GENERAL_CATEGORY = 'General';
  const HIERARCHY_OTHER_SYSTEM = 'Other';
  let favorites = new Set();
  let draft = {};
  let arrayDraft = {};
  let bitDraft = {};
  let selected = null;
  let currentSettingsItem = null;
  let queuedSemanticWrite = null;
  let burnBaselineProfileFingerprint = '';
  const burnSemanticBaselines = new Map();
  const burnDirtySemanticKeys = new Set();
  let selectedArray = null;
  let selectedSurface = null;
  let arrayDetail = null;
  let arrayXDetail = null;
  let arrayYDetail = null;
  let selectedArrayCell = -1;
  let selectedArrayAnchor = -1;
  let selectedArrayCells = new Set();
  let arrayRangeSelectArmed = false;
  const MAX_TABLE_SELECTION = 2048;

  search.placeholder = 'Search hierarchy / feature';
  filters.style.display = 'none';

  try {
    const saved = JSON.parse(localStorage.getItem(favoritesKey) || '[]');
    if (Array.isArray(saved)) favorites = new Set(saved.filter(v => typeof v === 'string'));
  } catch (_) {}

  function draftKey() {
    if (!workspace || workspace.status !== 'ready') return null;
    return 'epicdash.t4.scalarDraft.v1:' + String(workspace.profileFingerprint || '') + ':' + String(workspace.tuneFingerprint || '');
  }

  function loadDraft() {
    draft = {};
    const key = draftKey();
    if (!key) return;
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '{}');
      if (saved && typeof saved === 'object' && !Array.isArray(saved)) draft = saved;
    } catch (_) {}
  }

  function arrayDraftKey() {
    if (!workspace || workspace.status !== 'ready') return null;
    return 'epicdash.t5.arrayDraft.v1:' + String(workspace.profileFingerprint || '') + ':' + String(workspace.tuneFingerprint || '');
  }

  function loadArrayDraft() {
    arrayDraft = {};
    const key = arrayDraftKey();
    if (!key) return;
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '{}');
      if (saved && typeof saved === 'object' && !Array.isArray(saved)) arrayDraft = saved;
    } catch (_) {}
  }

  function bitDraftKey() {
    if (!workspace || workspace.status !== 'ready') return null;
    return 'epicdash.t4.bitDraft.v1:' + String(workspace.profileFingerprint || '') + ':' + String(workspace.tuneFingerprint || '');
  }

  function loadBitDraft() {
    bitDraft = {};
    const key = bitDraftKey();
    if (!key) return;
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '{}');
      if (saved && typeof saved === 'object' && !Array.isArray(saved)) bitDraft = saved;
    } catch (_) {}
  }

  function saveDraft() {
    const key = draftKey();
    if (!key) return;
    try { localStorage.setItem(key, JSON.stringify(draft)); } catch (_) {}
  }

  function saveArrayDraft() {
    const key = arrayDraftKey();
    if (!key) return;
    try { localStorage.setItem(key, JSON.stringify(arrayDraft)); } catch (_) {}
  }

  function saveBitDraft() {
    const key = bitDraftKey();
    if (!key) return;
    try { localStorage.setItem(key, JSON.stringify(bitDraft)); } catch (_) {}
  }

  function saveFavorites() {
    try { localStorage.setItem(favoritesKey, JSON.stringify(Array.from(favorites).sort())); } catch (_) {}
  }

  function digitsOf(item) {
    return Math.max(0, Math.min(6, Number.isFinite(Number(item?.digits)) ? Number(item.digits) : 2));
  }

  function formatNumber(value, item) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(digitsOf(item)) : '—';
  }

  function displayUnit(item) {
    const raw = String(item?.unit || '').trim();
    if (!raw) return '';
    // TunerStudio permits dynamic expression units such as
    // {bitStringValue(fuelUnits, fuelAlgorithm)}. EpicDash does not own a
    // TunerStudio expression evaluator, so never present the raw expression
    // as though it were a resolved engineering unit.
    if (raw.startsWith('{') || raw.endsWith('}') || raw.includes('bitStringValue(')) return '';
    return raw;
  }

  function valueWithUnit(value, item) {
    const resolved = displayUnit(item);
    const unit = resolved ? ' ' + resolved : '';
    return formatNumber(value, item) + unit;
  }

  function rangeText(item) {
    const low = Number(item.low), high = Number(item.high);
    if (Number.isFinite(low) && Number.isFinite(high)) return `${low} … ${high}`;
    if (Number.isFinite(low)) return `≥ ${low}`;
    if (Number.isFinite(high)) return `≤ ${high}`;
    return '';
  }

  function draftFor(name) {
    const entry = draft[name];
    if (!entry || entry.tuneFingerprint !== workspace?.tuneFingerprint) return null;
    return entry;
  }

  function arrayDraftId(name, cellIndex) { return String(name) + ':' + String(cellIndex); }

  function arrayDraftFor(name, cellIndex) {
    const entry = arrayDraft[arrayDraftId(name, cellIndex)];
    if (!entry || entry.tuneFingerprint !== workspace?.tuneFingerprint) return null;
    return entry;
  }

  function bitDraftFor(name) {
    const entry = bitDraft[name];
    if (!entry || entry.tuneFingerprint !== workspace?.tuneFingerprint) return null;
    return entry;
  }

  function bitOptionForValue(bit, value) {
    return (Array.isArray(bit?.options) ? bit.options : []).find(option => Number(option.value) === Number(value)) || null;
  }

  function bitLabel(bit, value) {
    return bitOptionForValue(bit, value)?.label || String(value);
  }

  function isEncodedArrayNoOp(result) {
    return String(result?.status || '') === 'ready' && result?.noOp === true;
  }

  function arrayHasDraft(name) {
    return Object.values(arrayDraft).some(entry => entry && entry.arrayName === name && entry.tuneFingerprint === workspace?.tuneFingerprint);
  }

  function pendingChangeCount() {
    const scalarCount = Object.keys(draft).filter(name => draftFor(name)).length;
    const arrayCount = Object.values(arrayDraft).filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint).length;
    const bitCount = Object.keys(bitDraft).filter(name => bitDraftFor(name)).length;
    return scalarCount + arrayCount + bitCount;
  }

  function updateChangeCount() {
    const count = pendingChangeCount();
    changeCount.textContent = String(count);
    changesButton.classList.toggle('active', count > 0);
    if (settingsPending) settingsPending.textContent = count
      ? count + ' pending edit' + (count === 1 ? '' : 's') + ' bound to this ECU snapshot.'
      : 'No pending edits. Change a value and apply it first.';
    if (settingsWriteRam) {
      settingsWriteRam.disabled = count === 0 ||
        tuningWriteStatus?.operationRunning === true ||
        tuningWriteStatus?.uncertain === true;
      settingsWriteRam.textContent = count ? ('Write ' + count + ' pending to ECU RAM') : 'Write pending to ECU RAM';
    }
    updateNormalTuningControls();
  }


  function cleanMenuLabel(value) {
    return String(value || '').replace(/&/g, '').trim();
  }

  function conditionStateOf(item) {
    const state = item?.conditionState;
    return state && typeof state === 'object'
      ? state
      : {status:'active', enabled:true, visible:true, supported:true, reason:null, evaluations:[]};
  }

  function conditionVisible(item) { return conditionStateOf(item).visible !== false; }
  function conditionUsable(item) {
    const state = conditionStateOf(item);
    return state.visible !== false && state.enabled !== false && state.supported !== false;
  }

  function conditionText(item) {
    const list = Array.isArray(item?.conditions) ? item.conditions.filter(Boolean) :
      (Array.isArray(item) ? item.filter(Boolean) : []);
    if (!list.length) return '';
    const state = Array.isArray(item) ? null : conditionStateOf(item);
    const status = state ? String(state.status || 'active').toUpperCase() : 'PARSED';
    const reason = state?.reason ? (' • ' + String(state.reason)) : '';
    return 'INI condition • ' + status + ': ' + list.join(' • ') + reason;
  }

  function rebuildWorkspaceIndex() {
    const index = {scalars:new Map(), bitFields:new Map(), surfaces:new Map(), dialogs:new Map(), arrays:new Map()};
    for (const item of (workspace?.scalars || [])) index.scalars.set(String(item.name), item);
    for (const item of (workspace?.bitFields || [])) index.bitFields.set(String(item.name), item);
    for (const item of [].concat(workspace?.tables || [], workspace?.curves || [])) index.surfaces.set(String(item.id), item);
    for (const item of (workspace?.dialogs || [])) index.dialogs.set(String(item.id), item);
    for (const item of (workspace?.arrays || [])) index.arrays.set(String(item.name), item);
    workspaceIndex = index;
    const profileFingerprint = String(workspace?.profileFingerprint || '');
    if (profileFingerprint !== workspaceIndexProfileFingerprint) {
      workspaceIndexProfileFingerprint = profileFingerprint;
      groupedRouteBlueprintCache = new Map();
    }
    groupedRoutesCache = new Map();
    hierarchyCacheKey = '';
    hierarchyCache = null;
    if (!Array.isArray(workspace?.menuItems) || workspace.menuItems.length === 0) {
      hierarchySystem = '';
      hierarchyCategory = '';
      hierarchyFeatureId = '';
      hierarchyTableId = '';
      hierarchyCurveId = '';
      curveActiveId = '';
      curveLoadedIds = [];
      curveVisibility = new Set();
    }
  }

  function workspaceScalar(name) { return workspaceIndex?.scalars.get(String(name)) || null; }
  function workspaceBitField(name) { return workspaceIndex?.bitFields.get(String(name)) || null; }
  function workspaceSurface(id) { return workspaceIndex?.surfaces.get(String(id)) || null; }
  function workspaceDialog(id) { return workspaceIndex?.dialogs.get(String(id)) || null; }
  function workspaceArray(name) { return workspaceIndex?.arrays.get(String(name)) || null; }

  function makeConditionNode(item) {
    const text = conditionText(item);
    if (!text) return null;
    const state = Array.isArray(item) ? null : conditionStateOf(item);
    const node = document.createElement('div');
    node.className = 't4tw-condition' +
      (state?.supported === false ? ' unsupported' : state?.enabled === false ? ' disabled' : '');
    node.textContent = text;
    return node;
  }

  function rerenderOpenSettingsDialog() {
    if (settingsDialog.hidden || !currentSettingsItem) return;
    const dialog = workspaceDialog(currentSettingsItem.dialogId);
    if (!dialog) return;
    settingsBody.textContent = '';
    renderSettingsDialogInto(settingsBody, dialog.id, [], 0, conditionUsable(currentSettingsItem));
  }

  function stageBitFieldEdit(bit, requestedValue) {
    if (bit?.writeAllowed === false) {
      meta.textContent = bit.writeBlockReason || 'Current INI conditions block this bit/enum field.';
      return false;
    }
    const requested = Number(requestedValue);
    if (!Number.isInteger(requested) || !workspace || !bit?.name) return false;
    let result;
    try {
      const raw = window.EpicDashAndroid?.previewTuningBitFieldJson?.(bit.name, requested);
      result = raw ? JSON.parse(raw) : {status:'error', reason:'Native bit/enum preview bridge unavailable'};
    } catch (error) {
      result = {status:'error', reason:String(error && error.message || error)};
    }
    if (result.status !== 'ready') {
      meta.textContent = result.reason || 'Bit/enum edit rejected.';
      return false;
    }
    if (result.generation !== workspace.generation ||
        result.profileFingerprint !== workspace.profileFingerprint ||
        result.tuneFingerprint !== workspace.tuneFingerprint) {
      meta.textContent = 'Bit/enum edit identity is stale; Read ECU or refresh before continuing.';
      return false;
    }

    if (result.noOp === true) {
      delete bitDraft[bit.name];
    } else {
      if (result.writeEligible !== true || Number(result.changedBytes || 0) <= 0) {
        meta.textContent = 'Bit/enum semantic preview did not authorize a pending write.';
        return false;
      }
      bitDraft[bit.name] = {
        kind:'bitField',
        name:bit.name,
        currentValue:Number(result.currentValue),
        currentLabel:bitLabel(bit, result.currentValue),
        requestedValue:Number(result.requestedValue),
        effectiveValue:Number(result.effectiveValue),
        effectiveLabel:bitLabel(bit, result.effectiveValue),
        changedBytes:Number(result.changedBytes || 0),
        generation:Number(result.generation),
        profileFingerprint:String(result.profileFingerprint || ''),
        tuneFingerprint:String(result.tuneFingerprint || '')
      };
    }
    saveBitDraft();
    updateChangeCount();
    const change = currentBitWrite(bit.name);
    if (change) queueSemanticWrites([change], null);
    rerenderOpenSettingsDialog();
    if (viewMode === 'settings') render();
    return true;
  }

  function parameterGroupGlyph(title) {
    const value=String(title||'').toLowerCase();
    if(value.includes('fuel')) return '⛽';
    if(value.includes('detect')||value.includes('trigger')) return '⌁';
    if(value.includes('condition')||value.includes('limit')) return '☷';
    if(value.includes('behav')||value.includes('mode')) return '↗';
    if(value.includes('idle')) return '◔';
    if(value.includes('ignition')||value.includes('spark')) return 'ϟ';
    if(value.includes('boost')) return '◎';
    return '⚙';
  }

  function stageInlineScalarEdit(scalar, requested, inputNode) {
    if (!scalar || scalar.writeAllowed === false || !workspace) return false;
    if (!Number.isFinite(Number(requested))) {
      if (inputNode) inputNode.title='Enter a finite engineering value.';
      return false;
    }
    let result;
    try {
      const raw=window.EpicDashAndroid?.previewTuningScalarJson?.(scalar.name,Number(requested));
      result=raw?JSON.parse(raw):{status:'error',reason:'Native semantic preview bridge unavailable'};
    } catch(error) {
      result={status:'error',reason:String(error&&error.message||error)};
    }
    if(result.status!=='ready' ||
       result.generation!==workspace.generation ||
       result.profileFingerprint!==workspace.profileFingerprint ||
       result.tuneFingerprint!==workspace.tuneFingerprint) {
      if(inputNode) inputNode.title=result.reason||'Edit identity is stale; refresh the current ECU tune.';
      return false;
    }
    if(result.noOp===true) {
      delete draft[scalar.name];
      saveDraft();
      if(inputNode){inputNode.value=formatNumber(result.effectiveValue,scalar);inputNode.title='';}
      updateChangeCount();
      return true;
    }
    if(result.writeEligible!==true || Number(result.changedBytes||0)<=0) {
      if(inputNode) inputNode.title=result.reason||'Semantic edit is not write eligible.';
      return false;
    }
    draft[scalar.name]={
      name:scalar.name,currentValue:Number(result.currentValue),requestedValue:Number(result.requestedValue),
      effectiveValue:Number(result.effectiveValue),unit:String(result.unit||scalar.unit||''),digits:Number(scalar.digits||0),
      changedBytes:Number(result.changedBytes||0),generation:Number(result.generation),
      profileFingerprint:String(result.profileFingerprint||''),tuneFingerprint:String(result.tuneFingerprint||''),savedAt:Date.now()
    };
    saveDraft();
    updateChangeCount();
    const change=currentScalarWrite(scalar.name);
    if(change) queueSemanticWrites([change],null);
    if(inputNode) inputNode.title='Validated and queued for verified ECU RAM write.';
    return true;
  }

  function settingsFieldNode(entry, inheritedEnabled = true) {
    const target = String(entry.target || '');
    const scalar = workspaceScalar(target);
    const bit = workspaceBitField(target);
    const surface = workspaceSurface(target);
    const row = document.createElement(surface ? 'button' : 'div');
    if (row.tagName === 'BUTTON') row.type = 'button';
    const targetWriteAllowed = (scalar || bit || surface)?.writeAllowed !== false;
    const entryEnabled = inheritedEnabled && conditionUsable(entry) && targetWriteAllowed;
    row.className = 't4tw-settings-field' + (scalar || bit || surface ? ' editable' : '') + (entryEnabled ? '' : ' blocked');
    if (row.tagName === 'BUTTON') row.disabled = !entryEnabled;
    if (scalar && draftFor(scalar.name)) row.classList.add('pending');
    if (bit && bitDraftFor(bit.name)) row.classList.add('pending');

    const left = document.createElement('div');
    const label = document.createElement('div');
    label.className = 't4tw-settings-field-label';
    label.textContent = entry.label || target || 'Setting';
    const key = document.createElement('div');
    key.className = 't4tw-settings-field-key';
    key.textContent = target || 'text';
    left.append(label, key);
    const condition = makeConditionNode(entry);
    if (condition) left.appendChild(condition);

    const right = document.createElement('div');
    right.className = 't4tw-settings-field-value';
    if (scalar) {
      const pending = draftFor(scalar.name);
      const wrap = document.createElement('div');
      wrap.className = 't4tw-param-input-wrap';
      const input = document.createElement('input');
      input.className = 't4tw-param-input';
      input.inputMode = 'decimal';
      input.value = formatNumber(pending ? pending.effectiveValue : scalar.value, scalar);
      input.disabled = !entryEnabled;
      input.setAttribute('aria-label', entry.label || scalar.name);
      const commit = () => {
        const requested=Number(input.value);
        if(!Number.isFinite(requested)){input.value=formatNumber(pending ? pending.effectiveValue : scalar.value,scalar);return;}
        stageInlineScalarEdit(scalar,requested,input);
      };
      input.addEventListener('focus',()=>input.select());
      input.addEventListener('change',commit);
      input.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();input.blur();}});
      const unit=document.createElement('span');
      unit.className='t4tw-param-unit';
      unit.textContent=displayUnit(scalar);
      wrap.append(input,unit);
      right.appendChild(wrap);
    } else if (bit) {
      const pending = bitDraftFor(bit.name);
      const effectiveValue = pending ? Number(pending.effectiveValue) : Number(bit.value);
      const optionsList = Array.isArray(bit.options) ? bit.options : [];
      if (optionsList.length === 2) {
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 't4tw-bit-control t4tw-bit-toggle' + (pending ? ' pending' : '') + (Number(effectiveValue)!==0 ? ' on' : '');
        toggle.textContent = bitLabel(bit, effectiveValue);
        toggle.disabled = !entryEnabled;
        toggle.onclick = event => {
          event.stopPropagation();
          const next = optionsList.find(option => Number(option.value) !== effectiveValue) || optionsList[0];
          stageBitFieldEdit(bit, Number(next.value));
        };
        right.appendChild(toggle);
      } else if (optionsList.length > 0) {
        const select = document.createElement('select');
        select.className = 't4tw-bit-control' + (pending ? ' pending' : '');
        select.disabled = !entryEnabled;
        for (const option of optionsList) {
          const node = document.createElement('option');
          node.value = String(option.value);
          node.textContent = option.label || String(option.value);
          select.appendChild(node);
        }
        select.value = String(effectiveValue);
        select.onchange = event => {
          event.stopPropagation();
          stageBitFieldEdit(bit, Number(select.value));
        };
        right.appendChild(select);
      } else {
        right.textContent = bit.valueLabel || String(bit.value);
      }
      const state = document.createElement('small');
      state.textContent = pending ? 'PENDING • semantic bit/enum edit' :
        (optionsList.length ? 'ENUM / BIT • editable' : 'ENUM / BIT • no parsed options');
      right.appendChild(state);
      if (optionsList.length) {
        const options = document.createElement('div');
        options.className = 't4tw-enum-options';
        options.textContent = optionsList.slice(0, 12).map(option => option.label).join(' • ') +
          (optionsList.length > 12 ? ' …' : '');
        left.appendChild(options);
      }
    } else if (surface) {
      right.textContent = String(surface.kind || 'editor').toUpperCase();
      const state = document.createElement('small');
      state.textContent = 'tap to open table / curve';
      right.appendChild(state);
      row.onclick = entryEnabled ? () => openArrayEditor(surface) : null;
    } else {
      right.textContent = '—';
      const state = document.createElement('small');
      state.textContent = target ? 'unsupported / unavailable target' : 'text only';
      right.appendChild(state);
    }
    if (!entryEnabled && (scalar || bit || surface)) {
      const blocked = document.createElement('small');
      blocked.textContent = (scalar || bit || surface)?.writeBlockReason || conditionStateOf(entry).reason || 'disabled by current INI conditions';
      right.appendChild(blocked);
    }
    row.append(left, right);
    return row;
  }

  function renderSettingsDialogInto(container, dialogId, stack = [], depth = 0, inheritedEnabled = true) {
    const dialog = workspaceDialog(dialogId);
    if (!dialog) {
      const missing = document.createElement('div');
      missing.className = 't4tw-settings-text';
      missing.textContent = 'Panel ' + dialogId + ' is not represented by a parsed INI dialog.';
      container.appendChild(missing);
      return;
    }
    if (stack.includes(dialogId) || depth > 8) {
      const cycle = document.createElement('div');
      cycle.className = 't4tw-settings-text attention';
      cycle.textContent = 'Nested panel cycle/depth limit at ' + dialogId + '.';
      container.appendChild(cycle);
      return;
    }

    const section = document.createElement('section');
    section.className = 't4tw-settings-panel t4tw-settings-depth-' + depth + (depth === 0 ? ' t4tw-settings-root' : '');
    if (dialog.title || depth === 0) {
      const title = document.createElement('div');
      title.className = 't4tw-settings-panel-title';
      const titleText=dialog.title || dialog.id;
      if(depth>0){
        const icon=document.createElement('span');icon.className='t4tw-settings-panel-icon';icon.textContent=parameterGroupGlyph(titleText);
        const label=document.createElement('span');label.textContent=titleText;title.append(icon,label);
      }else{
        title.textContent=titleText;
      }
      section.appendChild(title);
    }

    for (const entry of (dialog.entries || [])) {
      if (!conditionVisible(entry)) continue;
      const entryEnabled = inheritedEnabled && conditionUsable(entry);
      if (entry.kind === 'field') {
        section.appendChild(settingsFieldNode(entry, inheritedEnabled));
      } else if (entry.kind === 'text') {
        const textNode = document.createElement('div');
        textNode.className = 't4tw-settings-text' + (String(entry.label || '').trim().startsWith('!') ? ' attention' : '');
        textNode.textContent = String(entry.label || '').replace(/^!\s*/, '');
        const condition = makeConditionNode(entry);
        if (condition) textNode.appendChild(condition);
        section.appendChild(textNode);
      } else if (entry.kind === 'panel') {
        const wrapper = document.createElement('div');
        wrapper.className = 't4tw-settings-panel-wrap';
        const condition = makeConditionNode(entry);
        if (condition) wrapper.appendChild(condition);
        renderSettingsDialogInto(wrapper, String(entry.target || ''), stack.concat(dialogId), depth + 1, entryEnabled);
        section.appendChild(wrapper);
      } else if (entry.kind === 'command') {
        const command = document.createElement('button');
        command.type = 'button';
        command.className = 't4tw-settings-command';
        command.disabled = true;
        command.textContent = (entry.label || entry.target || 'Command') + ' • ECU command not exposed';
        const condition = makeConditionNode(entry);
        section.appendChild(command);
        if (condition) section.appendChild(condition);
      }
    }
    container.appendChild(section);
  }

  function openSettingsDialog(item) {
    if (!conditionVisible(item) || !conditionUsable(item)) {
      meta.textContent = conditionStateOf(item).reason || 'Current INI conditions block this menu item.';
      return;
    }
    currentSettingsItem = item;
    const dialog = workspaceDialog(item.dialogId);
    if (!dialog) {
      const surface = workspaceSurface(item.dialogId);
      if (surface) {
        openArrayEditor(surface);
        return;
      }
      meta.textContent = 'INI target ' + item.dialogId + ' is not a parsed dialog/table/curve in this slice.';
      return;
    }
    settingsTitle.textContent = dialog.title || item.title || dialog.id;
    settingsBreadcrumb.textContent = [
      cleanMenuLabel(item.menu) || HIERARCHY_OTHER_SYSTEM,
      cleanMenuLabel(item.group) || HIERARCHY_GENERAL_CATEGORY,
      item.title || item.dialogId
    ].join(' › ');
    settingsHelp.textContent = dialog.topicHelp ? ('TunerStudio help topic: ' + dialog.topicHelp) : '';
    settingsBody.textContent = '';
    renderSettingsDialogInto(settingsBody, dialog.id, [], 0, conditionUsable(item));
    settingsDialog.hidden = false;
  }

  const PRESENTATION_SYSTEM_ORDER = ['Fuel','Ignition','Idle','Boost','Start','Limiters','Inputs','Outputs','Diagnostics','Advanced'];
  function presentationSystemName(rawValue) {
    const raw = cleanMenuLabel(rawValue);
    const normalized = raw.toLowerCase();
    if (!raw) return 'Advanced';
    if (normalized.includes('fuel')) return 'Fuel';
    if (normalized.includes('ignition') || normalized === 'spark') return 'Ignition';
    if (normalized.includes('idle')) return 'Idle';
    if (normalized.includes('boost')) return 'Boost';
    if (normalized.includes('crank') || normalized.includes('start')) return 'Start';
    if (normalized.includes('limit')) return 'Limiters';
    if (normalized.includes('input')) return 'Inputs';
    if (normalized.includes('output')) return 'Outputs';
    if (normalized.includes('diagnostic')) return 'Diagnostics';
    return 'Advanced';
  }

  function presentationCategoryName(rawSystem, rawGroup) {
    const system = presentationSystemName(rawSystem);
    const group = cleanMenuLabel(rawGroup) || HIERARCHY_GENERAL_CATEGORY;
    const raw = cleanMenuLabel(rawSystem);
    if (system === 'Advanced' && raw && raw.toLowerCase() !== 'advanced') {
      return raw + (group === HIERARCHY_GENERAL_CATEGORY ? '' : ' · ' + group);
    }
    return group;
  }

  function sortPresentationSystems(systems) {
    const order = new Map(PRESENTATION_SYSTEM_ORDER.map((name,index)=>[name,index]));
    systems.sort((a,b)=>(order.get(a.name) ?? 999)-(order.get(b.name) ?? 999) || a.name.localeCompare(b.name));
    return systems;
  }

  function systemIconSvg(name) {
    const icons = {
      Fuel:'<svg viewBox="0 0 24 24"><path d="M6 3h9v18H6z"/><path d="M8 7h5"/><path d="M15 8h2l2 2v7c0 1 2 1 2 0v-6l-2-2"/></svg>',
      Ignition:'<svg viewBox="0 0 24 24"><path d="M13 2L5 13h6l-1 9 8-12h-6z"/></svg>',
      Idle:'<svg viewBox="0 0 24 24"><path d="M4 16a8 8 0 1 1 16 0"/><path d="M12 13l3-4"/><path d="M7 19h10"/></svg>',
      Boost:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 4c3 2 4 4 3 6M20 12c-2 3-4 4-6 3M12 20c-3-2-4-4-3-6M4 12c2-3 4-4 6-3"/></svg>',
      Start:'<svg viewBox="0 0 24 24"><path d="M12 2v9"/><path d="M7 5a8 8 0 1 0 10 0"/></svg>',
      Limiters:'<svg viewBox="0 0 24 24"><path d="M12 3l10 18H2z"/><path d="M12 9v5M12 17v1"/></svg>',
      Inputs:'<svg viewBox="0 0 24 24"><path d="M3 12h14"/><path d="M12 7l5 5-5 5"/><path d="M19 5v14"/></svg>',
      Outputs:'<svg viewBox="0 0 24 24"><rect x="4" y="6" width="16" height="12"/><path d="M8 3v3M12 3v3M16 3v3M8 18v3M12 18v3M16 18v3"/></svg>',
      Diagnostics:'<svg viewBox="0 0 24 24"><path d="M2 13h4l2-7 4 14 3-9 2 4h5"/></svg>',
      Advanced:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></svg>'
    };
    return icons[name] || icons.Advanced;
  }


  function settingsHierarchy(q) {
    const query = String(q || '').trim().toLowerCase();
    const cacheKey = [workspace?.profileFingerprint || '', workspace?.tuneFingerprint || '', query].join('|');
    if (hierarchyCache && hierarchyCacheKey === cacheKey) return hierarchyCache;
    const systems = [];
    const systemMap = new Map();

    for (const item of (Array.isArray(workspace?.menuItems) ? workspace.menuItems : [])) {
      if (!conditionVisible(item)) continue;
      if (query && ![item.menu, item.group, item.title, item.dialogId]
          .some(value => String(value || '').toLowerCase().includes(query))) continue;

      const systemName = presentationSystemName(item.menu);
      const categoryName = presentationCategoryName(item.menu, item.group);
      let system = systemMap.get(systemName);
      if (!system) {
        system = {name:systemName, categories:[], categoryMap:new Map(), itemCount:0};
        systemMap.set(systemName, system);
        systems.push(system);
      }
      let category = system.categoryMap.get(categoryName);
      if (!category) {
        category = {name:categoryName, items:[]};
        system.categoryMap.set(categoryName, category);
        system.categories.push(category);
      }
      category.items.push(item);
      system.itemCount++;
    }

    sortPresentationSystems(systems);
    hierarchyCacheKey = cacheKey;
    hierarchyCache = systems;
    return systems;
  }

  function parameterFeatures(category) {
    return (category?.items || []).filter(item => !!workspaceDialog(item.dialogId));
  }

  function ensureHierarchySelection(systems) {
    if (!systems.length) {
      hierarchySystem = '';
      hierarchyCategory = '';
      hierarchyFeatureId = '';
      return {system:null, category:null, feature:null};
    }
    const system = systems.find(entry => entry.name === hierarchySystem) ||
      systems.find(entry => entry.name === 'Fuel') || systems[0];
    hierarchySystem = system.name;
    const category = system.categories.find(entry => entry.name === hierarchyCategory) ||
      (!hierarchyCategory ? system.categories.find(entry => /transient/i.test(entry.name)) : null) ||
      system.categories[0] || null;
    hierarchyCategory = category?.name || '';
    const features = parameterFeatures(category);
    const feature = features.find(item => String(item.dialogId) === hierarchyFeatureId) ||
      (!hierarchyFeatureId ? features.find(item => /accel|enrich/i.test(String(item.title || item.dialogId || ''))) : null) ||
      features[0] || null;
    hierarchyFeatureId = feature ? String(feature.dialogId || '') : '';
    return {system, category, feature};
  }

  function hierarchySelect(label, value, options, onChange) {
    const wrap = document.createElement('label');
    wrap.className = 't4tw-hierarchy-select-wrap';
    wrap.dataset.hierarchyLevel = String(label || '').toLowerCase();
    const caption = document.createElement('span');
    caption.className = 't4tw-hierarchy-select-label';
    caption.textContent = label;
    const select = document.createElement('select');
    select.className = 't4tw-hierarchy-select';
    select.setAttribute('aria-label', label);
    for (const spec of options) {
      const option = document.createElement('option');
      option.value = String(spec.value);
      option.textContent = spec.label;
      option.disabled = spec.disabled === true;
      select.appendChild(option);
    }
    select.value = String(value ?? '');
    select.onchange = () => onChange(select.value);
    wrap.append(caption, select);
    return wrap;
  }

  function hierarchyChevron(from = '') {
    const node = document.createElement('span');
    node.className = 't4tw-hierarchy-chevron';
    node.setAttribute('aria-hidden', 'true');
    if (from) node.dataset.hierarchyFrom = from;
    node.textContent = '›';
    return node;
  }

  function modeSvg(mode) {
    if (mode === 'settings') return '<svg viewBox="0 0 24 24"><path d="M4 6h16M7 6v0M4 12h16M15 12v0M4 18h16M10 18v0"/></svg>';
    if (mode === 'tables') return '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16"/><path d="M4 9h16M4 15h16M9 4v16M15 4v16"/></svg>';
    return '<svg viewBox="0 0 24 24"><path d="M3 19h18M5 17l4-5 4 2 6-8"/></svg>';
  }

  function setViewMode(nextMode) {
    const allowed = ['settings','tables','curves'];
    viewMode = allowed.includes(nextMode) ? nextMode : 'settings';
    modes.querySelectorAll('[data-mode]').forEach(node => node.classList.toggle('active', node.dataset.mode === viewMode));
    search.placeholder = viewMode === 'tables'
      ? 'Search current INI tables'
      : viewMode === 'curves'
        ? 'Search current INI curves'
        : 'Search hierarchy / feature';
    filters.style.display = 'none';
    if (viewMode === 'tables') tableFitPending = true;
    render();
  }

  function modeStrip() {
    const strip = document.createElement('div');
    strip.className = 't4tw-content-modes';
    for (const spec of [
      {mode:'settings',label:'Parameters'},
      {mode:'tables',label:'Tables'},
      {mode:'curves',label:'Curves'}
    ]) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 't4tw-content-mode' + (viewMode === spec.mode ? ' active' : '');
      button.dataset.mode = spec.mode;
      button.innerHTML = modeSvg(spec.mode) + '<span>' + spec.label + '</span>';
      button.onclick = () => setViewMode(spec.mode);
      strip.appendChild(button);
    }
    return strip;
  }

  function appendResponsiveNav(mainPane, selectors) {
    const row = document.createElement('div');
    row.className = 't4tw-nav-row';
    row.append(selectors, modeStrip());
    mainPane.appendChild(row);
  }

  function renderSettingsMode(q) {
    const systems = settingsHierarchy(q);
    const selectedHierarchy = ensureHierarchySelection(systems);
    const fragment = document.createDocumentFragment();

    if (!systems.length) {
      const empty = document.createElement('div');
      empty.className = 't4tw-empty';
      empty.textContent = q
        ? 'No current-INI features match this search.'
        : 'No visible current-INI tuning features are available.';
      fragment.appendChild(empty);
      list.textContent = '';
      list.appendChild(fragment);
      summary.textContent = '0 Systems • 0 Categories • 0 Features';
      updateChangeCount();
      return;
    }

    const shell = document.createElement('div');
    shell.className = 't4tw-parameters-shell';
    shell.dataset.uiContract = 'responsive-parameters-v1';

    const rail = renderSystemRail(systems, selectedHierarchy.system?.name || hierarchySystem, system => {
      hierarchySystem = system.name;
      hierarchyCategory = system.categories[0]?.name || '';
      hierarchyFeatureId = '';
      render();
    });

    const mainPane = document.createElement('div');
    mainPane.className = 't4tw-parameters-main';

    const selectedSystem = selectedHierarchy.system;
    const selectedCategory = selectedHierarchy.category;
    const selectedFeature = selectedHierarchy.feature;
    const selectors = document.createElement('div');
    selectors.className = 't4tw-hierarchy-selectors';
    selectors.dataset.uiContract = 'responsive-hierarchy-v1';

    const systemSelect = hierarchySelect(
      'System',
      selectedSystem?.name || '',
      systems.map(system => ({value:system.name, label:system.name})),
      value => {
        hierarchySystem = value;
        const nextSystem = systems.find(system => system.name === value);
        hierarchyCategory = nextSystem?.categories?.[0]?.name || '';
        hierarchyFeatureId = '';
        render();
      }
    );

    const categories = selectedSystem?.categories || [];
    const categorySelect = hierarchySelect(
      'Category',
      selectedCategory?.name || '',
      categories.map(category => ({value:category.name, label:category.name})),
      value => {
        hierarchyCategory = value;
        hierarchyFeatureId = '';
        render();
      }
    );

    const features = parameterFeatures(selectedCategory);
    const featureOptions = features.length
      ? features.map(item => ({
          value:String(item.dialogId || ''),
          label:item.title || item.dialogId,
          disabled:!conditionUsable(item)
        }))
      : [{value:'', label:'No parameter features', disabled:true}];
    const featureSelect = hierarchySelect(
      'Feature',
      selectedFeature?.dialogId || '',
      featureOptions,
      value => {
        hierarchyFeatureId = String(value || '');
        render();
      }
    );

    selectors.append(
      systemSelect,
      hierarchyChevron('system'),
      categorySelect,
      hierarchyChevron('category'),
      featureSelect
    );
    appendResponsiveNav(mainPane, selectors);

    if (selectedFeature) {
      const dialog = workspaceDialog(selectedFeature.dialogId);
      const head = document.createElement('div');
      head.className = 't4tw-parameter-feature-head';
      const headText = document.createElement('div');
      const title = document.createElement('div');
      title.className = 't4tw-parameter-feature-title';
      title.textContent = dialog?.title || selectedFeature.title || selectedFeature.dialogId;
      const path = document.createElement('div');
      path.className = 't4tw-parameter-feature-path';
      path.textContent = [selectedSystem?.name, selectedCategory?.name, selectedFeature.title || selectedFeature.dialogId]
        .filter(Boolean).join(' › ');
      headText.append(title, path);
      head.appendChild(headText);
      mainPane.appendChild(head);

      const body = document.createElement('div');
      body.className = 't4tw-inline-parameters';
      if (dialog) {
        renderSettingsDialogInto(body, dialog.id, [], 0, conditionUsable(selectedFeature));
      } else {
        const unavailable = document.createElement('div');
        unavailable.className = 't4tw-empty';
        unavailable.textContent = 'This feature has no parsed parameter dialog.';
        body.appendChild(unavailable);
      }
      mainPane.appendChild(body);

      const actions = document.createElement('div');
      actions.className = 't4tw-inline-actions';
      const pending = document.createElement('div');
      pending.className = 't4tw-inline-pending';
      const count = pendingChangeCount();
      pending.textContent = count > 0 ? (count + ' pending semantic edit' + (count === 1 ? '' : 's')) : 'No pending edits';
      const write = document.createElement('button');
      write.type = 'button';
      write.className = 't4tw-btn';
      write.textContent = count > 0 ? ('Write ' + count + ' pending to RAM') : 'Write pending to RAM';
      write.disabled = count === 0 || tuningWriteStatus?.operationRunning === true || tuningWriteStatus?.uncertain === true;
      write.onclick = writeAllDrafts;
      actions.append(pending, write);
      mainPane.appendChild(actions);
    } else {
      const empty = document.createElement('div');
      empty.className = 't4tw-empty';
      empty.textContent = 'No parameter dialog is available in this category. Use Tables / Curves for calibration surfaces.';
      mainPane.appendChild(empty);
    }

    shell.append(rail, mainPane);
    fragment.appendChild(shell);
    list.textContent = '';
    list.appendChild(fragment);

    const categoryCount = systems.reduce((sum, system) => sum + system.categories.length, 0);
    const featureCount = systems.reduce((sum, system) => sum + system.itemCount, 0);
    const parameterFeatureCount = systems.reduce((sum, system) =>
      sum + system.categories.reduce((inner, category) => inner + parameterFeatures(category).length, 0), 0);
    const compat = workspace.iniCompatibility || {};
    summary.textContent =
      systems.length + ' System' + (systems.length === 1 ? '' : 's') + ' • ' +
      categoryCount + ' Categor' + (categoryCount === 1 ? 'y' : 'ies') + ' • ' +
      parameterFeatureCount + ' Parameter Feature' + (parameterFeatureCount === 1 ? '' : 's') + ' / ' +
      featureCount + ' total • INI ' + String(compat.state || 'amber').toUpperCase() + ' • ' +
      Number(compat.unsupportedConditionExpressions || 0) + ' unsupported condition(s)';
    updateChangeCount();
  }

  function openEditor(item) {
    if (item?.writeAllowed === false) {
      meta.textContent = item.writeBlockReason || 'Current INI conditions block this setting.';
      return;
    }
    selected = item;
    const d = draftFor(item.name);
    editorTitle.textContent = item.name;
    currentNode.textContent = valueWithUnit(item.value, item);
    requestedNode.textContent = d ? valueWithUnit(d.requestedValue, item) : '—';
    effectiveNode.textContent = d ? valueWithUnit(d.effectiveValue, item) : '—';
    requestedInput.value = d ? String(d.requestedValue) : String(item.value);
    editorStatus.className = 't4tw-status' + (d ? ' good' : '');
    editorStatus.textContent = d
      ? `PENDING EDIT • ${d.changedBytes} encoded byte(s) will change • not yet written to ECU`
      : 'Enter a value and Apply edit. It remains pending until written to ECU RAM.';
    applyRamButton.hidden = false;
    applyRamButton.disabled = !d || tuningWriteStatus?.operationRunning === true || tuningWriteStatus?.uncertain === true;
    applyRamButton.textContent = 'Write this edit to ECU RAM';
    editor.hidden = false;
    setTimeout(() => requestedInput.focus(), 0);
  }

  function closeEditor() { editor.hidden = true; selected = null; }

  function stageSelectedEdit() {
    if (!selected || !workspace) return;
    const requested = Number(requestedInput.value);
    if (!Number.isFinite(requested)) {
      editorStatus.className = 't4tw-status bad';
      editorStatus.textContent = 'Enter a finite engineering value.';
      return;
    }
    let result;
    try {
      const raw = window.EpicDashAndroid?.previewTuningScalarJson?.(selected.name, requested);
      result = raw ? JSON.parse(raw) : {status:'error', reason:'Native semantic preview bridge unavailable'};
    } catch (error) {
      result = {status:'error', reason:String(error && error.message || error)};
    }
    if (result.status !== 'ready') {
      editorStatus.className = 't4tw-status bad';
      editorStatus.textContent = result.reason || 'Edit rejected';
      requestedNode.textContent = valueWithUnit(requested, selected);
      effectiveNode.textContent = '—';
      applyRamButton.hidden = false;
      applyRamButton.disabled = true;
      return;
    }
    if (result.generation !== workspace.generation || result.profileFingerprint !== workspace.profileFingerprint || result.tuneFingerprint !== workspace.tuneFingerprint) {
      editorStatus.className = 't4tw-status bad';
      editorStatus.textContent = 'Edit identity is stale; Read ECU or refresh before continuing.';
      return;
    }
    if (result.noOp === true) {
      const hadPending = !!draft[selected.name];
      delete draft[selected.name];
      saveDraft();
      requestedNode.textContent = valueWithUnit(result.requestedValue, selected);
      effectiveNode.textContent = valueWithUnit(result.effectiveValue, selected);
      editorStatus.className = 't4tw-status';
      editorStatus.textContent = 'No encoded ECU change' + (hadPending ? '; the previous pending edit was removed.' : '.');
      applyRamButton.hidden = false;
      applyRamButton.disabled = true;
      updateChangeCount();
      render();
      return;
    }
    if (result.writeEligible !== true || Number(result.changedBytes || 0) <= 0) {
      editorStatus.className = 't4tw-status bad';
      editorStatus.textContent = 'Semantic preview did not authorize a pending write.';
      return;
    }
    draft[selected.name] = {
      name:selected.name,
      currentValue:Number(result.currentValue),
      requestedValue:Number(result.requestedValue),
      effectiveValue:Number(result.effectiveValue),
      unit:String(result.unit || selected.unit || ''),
      digits:Number(selected.digits || 0),
      changedBytes:Number(result.changedBytes || 0),
      generation:Number(result.generation),
      profileFingerprint:String(result.profileFingerprint || ''),
      tuneFingerprint:String(result.tuneFingerprint || ''),
      savedAt:Date.now()
    };
    saveDraft();
    requestedNode.textContent = valueWithUnit(result.requestedValue, selected);
    effectiveNode.textContent = valueWithUnit(result.effectiveValue, selected);
    editorStatus.className = 't4tw-status good';
    editorStatus.textContent = 'Validated edit • sending to ECU RAM for exact verification…';
    applyRamButton.hidden = true;
    updateChangeCount();
    const change = currentScalarWrite(selected.name);
    if (change && queueSemanticWrites([change], editorStatus)) {
      editor.hidden = true;
    } else {
      render();
    }
  }

  function burnSemanticKey(change) {
    const kind=String(change?.kind||''), name=String(change?.name||'');
    if(!name)return '';
    if(kind==='arrayCell')return 'array:'+name;
    if(kind==='bitField')return 'bit:'+name;
    if(kind==='scalar')return 'scalar:'+name;
    return '';
  }

  function semanticCurrentValueForBurn(change) {
    const kind=String(change?.kind||''), name=String(change?.name||'');
    if(kind==='scalar') {
      const item=workspaceScalar(name);
      return item ? Number(item.value) : null;
    }
    if(kind==='bitField') {
      const item=workspaceBitField(name);
      return item ? Number(item.value) : null;
    }
    if(kind==='arrayCell') {
      const detail=getSemanticArrayDetail(name);
      return currentArrayIdentity(detail) && Array.isArray(detail.values) ? detail.values.map(Number) : null;
    }
    return null;
  }

  function semanticBurnValuesEqual(left,right) {
    if(Array.isArray(left)||Array.isArray(right)) {
      if(!Array.isArray(left)||!Array.isArray(right)||left.length!==right.length)return false;
      for(let index=0;index<left.length;index++) if(Number(left[index])!==Number(right[index]))return false;
      return true;
    }
    return Number(left)===Number(right);
  }

  function resetSemanticBurnAuthority(profileFingerprint='') {
    burnBaselineProfileFingerprint=String(profileFingerprint||'');
    burnSemanticBaselines.clear();
    burnDirtySemanticKeys.clear();
  }

  function captureSemanticBurnBaselines(changes) {
    const profile=String(workspace?.profileFingerprint||'');
    if(burnBaselineProfileFingerprint!==profile)resetSemanticBurnAuthority(profile);
    for(const change of (changes||[])) {
      const key=burnSemanticKey(change);
      if(!key||burnSemanticBaselines.has(key))continue;
      const value=semanticCurrentValueForBurn(change);
      if(value!==null)burnSemanticBaselines.set(key,Array.isArray(value)?value.slice():value);
    }
  }

  function reconcileSemanticBurnDirty(changes) {
    for(const change of (changes||[])) {
      const key=burnSemanticKey(change);
      if(!key||!burnSemanticBaselines.has(key))continue;
      const current=semanticCurrentValueForBurn(change);
      const baseline=burnSemanticBaselines.get(key);
      if(current!==null&&semanticBurnValuesEqual(current,baseline))burnDirtySemanticKeys.delete(key);
      else burnDirtySemanticKeys.add(key);
    }
  }

  function currentScalarWrite(name) {
    const d = draftFor(name);
    if (!d) return null;
    return {kind:'scalar', name:name, requestedValue:Number(d.requestedValue)};
  }

  function currentArrayWrite(name, cellIndex) {
    const d = arrayDraftFor(name, cellIndex);
    if (!d) return null;
    return {kind:'arrayCell', name:name, cellIndex:Number(cellIndex), requestedValue:Number(d.requestedValue)};
  }

  function currentBitWrite(name) {
    const d = bitDraftFor(name);
    if (!d) return null;
    return {kind:'bitField', name:name, requestedValue:Number(d.requestedValue)};
  }

  function clearQueuedDraftEntries(changes) {
    for (const change of (changes || [])) {
      const kind = String(change?.kind || '');
      if (kind === 'scalar') delete draft[String(change.name || '')];
      else if (kind === 'bitField') delete bitDraft[String(change.name || '')];
      else if (kind === 'arrayCell') delete arrayDraft[arrayDraftId(change.name, Number(change.cellIndex))];
    }
    saveDraft();
    saveBitDraft();
    saveArrayDraft();
    updateChangeCount();
  }

  function settleQueuedSemanticWriteAndReload() {
    readNativeWriteStatus();
    const queued = queuedSemanticWrite;
    const status = String(tuningWriteStatus?.status || '');
    const lastWrite = tuningWriteStatus?.lastWrite || null;
    const verified = !!queued &&
      status === 'ram_applied_verified' &&
      lastWrite &&
      String(lastWrite.baselineTuneFingerprint || '') === String(queued.baselineFingerprint || '') &&
      Number(lastWrite.changeCount || 0) === queued.changes.length;
    if (verified) {
      const verifiedChanges=queued.changes.map(change=>Object.assign({},change));
      clearQueuedDraftEntries(queued.changes);
      window.EpicDashHaptic?.('confirm');
      if (changesStatus) {
        changesStatus.className = 't4tw-status good';
        changesStatus.textContent = 'ECU RAM write verified. Pending edit(s) consumed; rebasing to the verified ECU snapshot.';
      }
      queuedSemanticWrite = null;
      loadWorkspace();
      reconcileSemanticBurnDirty(verifiedChanges);
      syncTunerChrome();
      return;
    } else if (queued && tuningWriteStatus?.operationRunning !== true) {
      if (changesStatus) {
        changesStatus.className = 't4tw-status bad';
        changesStatus.textContent = String(tuningWriteStatus?.detail || status || 'ECU RAM write did not complete.');
      }
      queuedSemanticWrite = null;
    }
    const burnStatus=String(tuningWriteStatus?.status||'');
    loadWorkspace();
    if(burnStatus==='saved'||burnStatus==='save_not_needed') {
      resetSemanticBurnAuthority(workspace?.profileFingerprint||'');
      syncTunerChrome();
    }
  }

  function queueSemanticWrites(changes, statusNode) {
    if (!workspace || workspace.status !== 'ready' || !Array.isArray(changes) || !changes.length) return false;
    if (tuningWriteStatus?.uncertain === true) {
      if (statusNode) {
        statusNode.className = 't4tw-status bad';
        statusNode.textContent = 'Previous write/save is uncertain. Use Read ECU before writing again.';
      }
      return false;
    }
    captureSemanticBurnBaselines(changes);
    const payload = {
      generation:Number(workspace.generation),
      profileFingerprint:String(workspace.profileFingerprint || ''),
      tuneFingerprint:String(workspace.tuneFingerprint || ''),
      changes:changes
    };
    try {
      const bridge = window.EpicDashAndroid?.writeTuningChangesJson;
      if (typeof bridge !== 'function') throw new Error('Native tuning write bridge unavailable');
      const raw = bridge.call(window.EpicDashAndroid, JSON.stringify(payload));
      const result = raw ? JSON.parse(raw) : {status:'error', reason:'No response from native tuning write bridge'};
      if (result.status !== 'queued') throw new Error(result.reason || result.detail || 'Tuning write was not queued');
      queuedSemanticWrite = {
        baselineFingerprint:String(workspace?.tuneFingerprint || ''),
        changes:changes.map(change => Object.assign({}, change))
      };
      if (changesStatus) {
        changesStatus.className = 't4tw-status good';
        changesStatus.textContent = changes.length + ' edit(s) accepted by native code and queued for ECU RAM verification.';
      }
      if (statusNode) {
        statusNode.className = 't4tw-status good';
        statusNode.textContent = 'ECU RAM write queued. Native code will C-write, R-read back, then verify the complete tune.';
      }
      return true;
    } catch (error) {
      if (statusNode) {
        statusNode.className = 't4tw-status bad';
        statusNode.textContent = String(error && error.message || error);
      } else {
        const message = String(error && error.message || error);
        meta.textContent = message;
        if (changesStatus) {
          changesStatus.className = 't4tw-status bad';
          changesStatus.textContent = 'WRITE ALL rejected: ' + message;
        }
      }
      return false;
    }
  }

  function writeSelectedScalar() {
    if (!selected || !workspace) return;
    const change = currentScalarWrite(selected.name);
    if (!change) {
      editorStatus.className = 't4tw-status bad';
      editorStatus.textContent = 'Apply the edit first so it is staged as a pending change.';
      return;
    }
    queueSemanticWrites([change], editorStatus);
  }

  function closeArrayEditor() {
    arrayEditor.hidden = true;
    selectedArray = null;
    selectedSurface = null;
    arrayDetail = null;
    arrayXDetail = null;
    arrayYDetail = null;
    selectedArrayCell = -1;
    selectedArrayAnchor = -1;
    selectedArrayCells = new Set();
    arrayRangeSelectArmed = false;
    updateArraySelectionControls();
  }

  function axisCell(label, onClick, axisSide = '') {
    const node = document.createElement(onClick ? 'button' : 'div');
    if (onClick) node.type = 'button';
    node.className = 't4tw-cell axis' + (onClick ? ' selectable' : '');
    if (axisSide) node.dataset.axisSide = axisSide;
    node.textContent = label;
    if (onClick) node.onclick = onClick;
    return node;
  }

  function tableRectIndices(anchorIndex, targetIndex, xCount, yCount) {
    const columns = Number(xCount), rows = Number(yCount);
    const anchor = Number(anchorIndex), target = Number(targetIndex);
    const total = columns * rows;
    if (!Number.isInteger(columns) || !Number.isInteger(rows) || columns <= 0 || rows <= 0 ||
        !Number.isInteger(anchor) || !Number.isInteger(target) ||
        anchor < 0 || target < 0 || anchor >= total || target >= total) return [];
    const ax = anchor % columns, ay = Math.floor(anchor / columns);
    const bx = target % columns, by = Math.floor(target / columns);
    const minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
    const minY = Math.min(ay, by), maxY = Math.max(ay, by);
    const result = [];
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) result.push(y * columns + x);
    }
    return result;
  }

  function tableRowIndices(rowIndex, xCount, yCount) {
    const row = Number(rowIndex), columns = Number(xCount), rows = Number(yCount);
    if (!Number.isInteger(row) || !Number.isInteger(columns) || !Number.isInteger(rows) ||
        columns <= 0 || rows <= 0 || row < 0 || row >= rows) return [];
    return Array.from({length:columns}, (_, x) => row * columns + x);
  }

  function tableColumnIndices(columnIndex, xCount, yCount) {
    const column = Number(columnIndex), columns = Number(xCount), rows = Number(yCount);
    if (!Number.isInteger(column) || !Number.isInteger(columns) || !Number.isInteger(rows) ||
        columns <= 0 || rows <= 0 || column < 0 || column >= columns) return [];
    return Array.from({length:rows}, (_, y) => y * columns + column);
  }

  function linearRangeIndices(anchorIndex, targetIndex, count) {
    const anchor = Number(anchorIndex), target = Number(targetIndex), total = Number(count);
    if (!Number.isInteger(anchor) || !Number.isInteger(target) || !Number.isInteger(total) ||
        total <= 0 || anchor < 0 || target < 0 || anchor >= total || target >= total) return [];
    const first = Math.min(anchor, target), last = Math.max(anchor, target);
    return Array.from({length:last - first + 1}, (_, offset) => first + offset);
  }

  function selectionOperationValue(mode, currentValue, operandValue) {
    const current = Number(currentValue), operand = Number(operandValue);
    if (!Number.isFinite(current) || !Number.isFinite(operand)) return Number.NaN;
    switch (String(mode || 'set')) {
      case 'add': return current + operand;
      case 'subtract': return current - operand;
      case 'percent': return current * (1 + operand / 100);
      case 'set': return operand;
      default: return Number.NaN;
    }
  }

  function selectedArrayIndices() {
    return Array.from(selectedArrayCells).filter(Number.isInteger).sort((a,b) => a - b);
  }

  function arrayBaseValue(index) {
    const pending = selectedArray ? arrayDraftFor(selectedArray.name, index) : null;
    if (pending && Number.isFinite(Number(pending.effectiveValue))) return Number(pending.effectiveValue);
    return Number(arrayDetail?.values?.[index]);
  }

  function updateArraySelectionControls() {
    const indices = selectedArrayIndices();
    const count = indices.length;
    const isTable = selectedSurface?.kind === 'table';
    const xCount = Number(selectedSurface?.xCount || 0);
    const hasFocus = Number.isInteger(selectedArrayCell) && selectedArrayCell >= 0;
    const busy = tuningWriteStatus?.operationRunning === true;
    const uncertain = tuningWriteStatus?.uncertain === true;
    if (selectionMeta) selectionMeta.textContent = count + ' cell' + (count === 1 ? '' : 's') + ' selected' +
      (arrayRangeSelectArmed ? ' • RANGE TARGET ARMED: tap the opposite cell' :
       (isTable ? ' • Shift-click = rectangle • Ctrl-click = toggle' : ' • Shift-click = range • Ctrl-click = toggle'));
    if (selectRowButton) selectRowButton.disabled = !isTable || !hasFocus || busy;
    if (selectColumnButton) selectColumnButton.disabled = !isTable || !hasFocus || busy;
    if (selectRangeButton) {
      selectRangeButton.disabled = !hasFocus || busy;
      selectRangeButton.classList.toggle('active', arrayRangeSelectArmed);
      selectRangeButton.textContent = arrayRangeSelectArmed ? 'Tap target cell…' : 'Rectangle / range to next cell';
    }
    if (selectAllButton) selectAllButton.disabled = !selectedArray || !arrayDetail || busy;
    if (selectClearButton) selectClearButton.disabled = count === 0 || busy;
    if (regionOperation) regionOperation.disabled = count === 0 || busy || uncertain;
    if (regionValue) regionValue.disabled = count === 0 || busy || uncertain;
    if (applySelectionButton) applySelectionButton.disabled = count === 0 || count > MAX_TABLE_SELECTION || busy || uncertain;
    if (isTable && hasFocus && xCount > 0) {
      const x = selectedArrayCell % xCount, y = Math.floor(selectedArrayCell / xCount);
      if (selectRowButton) selectRowButton.title = 'Select table row y' + y;
      if (selectColumnButton) selectColumnButton.title = 'Select table column x' + x;
    }
  }

  function replaceArraySelection(indices, focusIndex, anchorIndex = focusIndex) {
    const valid = (indices || []).filter(index => Number.isInteger(index) && index >= 0 && index < Number(arrayDetail?.values?.length || 0));
    selectedArrayCells = new Set(valid.slice(0, MAX_TABLE_SELECTION));
    selectedArrayCell = Number.isInteger(focusIndex) ? focusIndex : (valid[0] ?? -1);
    selectedArrayAnchor = Number.isInteger(anchorIndex) ? anchorIndex : selectedArrayCell;
    updateArraySelectionControls();
  }

  function selectArrayRow(rowIndex) {
    if (selectedSurface?.kind !== 'table') return;
    const indices = tableRowIndices(rowIndex, Number(selectedSurface.xCount), Number(selectedSurface.yCount));
    const focus = indices[0] ?? -1;
    replaceArraySelection(indices, focus, focus);
    if (focus >= 0) refreshArrayCellInspector(focus, false);
  }

  function selectArrayColumn(columnIndex) {
    if (selectedSurface?.kind !== 'table') return;
    const indices = tableColumnIndices(columnIndex, Number(selectedSurface.xCount), Number(selectedSurface.yCount));
    const focus = indices[0] ?? -1;
    replaceArraySelection(indices, focus, focus);
    if (focus >= 0) refreshArrayCellInspector(focus, false);
  }

  function selectAllArrayCells() {
    const count = Number(arrayDetail?.values?.length || 0);
    const indices = Array.from({length:Math.min(count, MAX_TABLE_SELECTION)}, (_, index) => index);
    const focus = selectedArrayCell >= 0 ? selectedArrayCell : (indices[0] ?? -1);
    replaceArraySelection(indices, focus, focus);
    if (focus >= 0) refreshArrayCellInspector(focus, false);
    else renderArrayGrid();
  }

  function clearArraySelection() {
    selectedArrayCells = new Set();
    selectedArrayAnchor = selectedArrayCell;
    updateArraySelectionControls();
    renderArrayGrid();
  }


  function valueCell(index, smallLabel) {
    const d = arrayDraftFor(selectedArray.name, index);
    const cell = document.createElement('button');
    cell.type = 'button';
    cell.className = 't4tw-cell' +
      (index === selectedArrayCell ? ' selected' : '') +
      (selectedArrayCells.has(index) ? ' multiselected' : '') +
      (d ? ' pending' : '');
    if (selectedSurface?.kind === 'table' && selectedArrayCells.has(index)) {
      const xCount = Number(selectedSurface.xCount || 0);
      const yCount = Number(selectedSurface.yCount || 0);
      if (xCount > 0 && yCount > 0) {
        const x = index % xCount;
        const y = Math.floor(index / xCount);
        if (y === 0 || !selectedArrayCells.has(index - xCount)) cell.classList.add('sel-top');
        if (y === yCount - 1 || !selectedArrayCells.has(index + xCount)) cell.classList.add('sel-bottom');
        if (x === 0 || !selectedArrayCells.has(index - 1)) cell.classList.add('sel-left');
        if (x === xCount - 1 || !selectedArrayCells.has(index + 1)) cell.classList.add('sel-right');
      }
    }
    const shown = d ? d.effectiveValue : arrayDetail.values[index];
    const minimum = Number(selectedArray?.minimum);
    const maximum = Number(selectedArray?.maximum);
    if (Number.isFinite(minimum) && Number.isFinite(maximum) && maximum > minimum && Number.isFinite(Number(shown))) {
      const ratio = Math.max(0, Math.min(1, (Number(shown) - minimum) / (maximum - minimum)));
      cell.classList.add('heat');
      cell.style.setProperty('--t4tw-heat-hue', String(Math.round(210 - ratio * 205)));
      cell.style.setProperty('--t4tw-heat-fg', ratio < 0.18 || ratio > 0.88 ? '#ffffff' : '#061018');
    }
    cell.innerHTML = '<small>' + smallLabel + '</small>' + formatNumber(shown, selectedArray);
    cell.onclick = event => selectArrayCell(index, event);
    return cell;
  }

  function renderArrayGridInto(grid, inline = false) {
    if (!grid) return;
    grid.textContent = '';
    if (!selectedArray || !selectedSurface || !arrayDetail || !Array.isArray(arrayDetail.values)) return;

    if (selectedSurface.kind === 'table' && Array.isArray(arrayXDetail?.values) && Array.isArray(arrayYDetail?.values)) {
      const xValues = arrayXDetail.values;
      const yValues = arrayYDetail.values;
      const xCount = Number(selectedSurface.xCount);
      const yCount = Number(selectedSurface.yCount);
      if (xCount > 0 && yCount > 0 && xValues.length === xCount && yValues.length === yCount && arrayDetail.values.length === xCount * yCount) {
        const cell = inline ? Math.round(22 * tableZoom) : 68;
        if (inline) grid.style.setProperty('--t4tw-inline-cell', cell + 'px');
        grid.style.gridTemplateColumns = (inline ? '52px ' : 'minmax(74px,auto) ') +
          'repeat(' + xCount + ', ' + (inline ? 'var(--t4tw-inline-cell)' : 'minmax(68px,1fr)') + ')';
        grid.appendChild(axisCell('Y \\ X', selectAllArrayCells));
        for (let x = 0; x < xCount; x++) {
          grid.appendChild(axisCell(valueWithUnit(xValues[x], arrayXDetail), () => selectArrayColumn(x), inline ? 'column' : ''));
        }
        for (let y = 0; y < yCount; y++) {
          grid.appendChild(axisCell(valueWithUnit(yValues[y], arrayYDetail), () => selectArrayRow(y), inline ? 'row' : ''));
          for (let x = 0; x < xCount; x++) {
            const index = y * xCount + x;
            grid.appendChild(valueCell(index, 'x' + x + ' • y' + y));
          }
        }
        return;
      }
    }

    if (selectedSurface.kind === 'curve' && Array.isArray(arrayXDetail?.values) && arrayXDetail.values.length === arrayDetail.values.length) {
      const columns = Math.min(8, Math.max(1, arrayDetail.values.length));
      grid.style.gridTemplateColumns = 'repeat(' + columns + ', minmax(82px,1fr))';
      for (let index = 0; index < arrayDetail.values.length; index++) {
        grid.appendChild(valueCell(index, 'X ' + valueWithUnit(arrayXDetail.values[index], arrayXDetail)));
      }
      return;
    }

    const dims = Array.isArray(arrayDetail.dimensions) ? arrayDetail.dimensions.map(Number) : [];
    const columns = dims.length > 1 ? Math.max(1, dims[0]) : Math.min(8, Math.max(1, arrayDetail.values.length));
    grid.style.gridTemplateColumns = 'repeat(' + columns + ', minmax(68px,1fr))';
    for (let index = 0; index < arrayDetail.values.length; index++) {
      grid.appendChild(valueCell(index, '#' + index));
    }
  }

  function renderArrayGrid() {
    renderArrayGridInto(arrayGrid, false);
    const inlineGrid = document.getElementById('t4twInlineTableGrid');
    if (inlineGrid && selectedSurface?.kind === 'table') renderArrayGridInto(inlineGrid, true);
    refreshInlineTableInspector();
  }

  function selectArrayCell(index, event = null) {
    if (!selectedArray || !arrayDetail || !Array.isArray(arrayDetail.values) || index < 0 || index >= arrayDetail.values.length) return;
    const toggle = !!(event?.ctrlKey || event?.metaKey);
    const extend = (arrayRangeSelectArmed || !!event?.shiftKey) && selectedArrayAnchor >= 0;
    if (extend) {
      arrayRangeSelectArmed = false;
      const indices = selectedSurface?.kind === 'table'
        ? tableRectIndices(selectedArrayAnchor, index, Number(selectedSurface.xCount), Number(selectedSurface.yCount))
        : linearRangeIndices(selectedArrayAnchor, index, arrayDetail.values.length);
      replaceArraySelection(indices, index, selectedArrayAnchor);
    } else if (toggle) {
      if (selectedArrayCells.has(index)) selectedArrayCells.delete(index);
      else if (selectedArrayCells.size < MAX_TABLE_SELECTION) selectedArrayCells.add(index);
      selectedArrayCell = index;
      selectedArrayAnchor = index;
      updateArraySelectionControls();
    } else {
      replaceArraySelection([index], index, index);
    }
    refreshArrayCellInspector(index, viewMode !== 'tables' && viewMode !== 'curves');
  }

  function refreshArrayCellInspector(index, focusInput = true) {
    if (!selectedArray || !arrayDetail || !Array.isArray(arrayDetail.values) || index < 0 || index >= arrayDetail.values.length) return;
    selectedArrayCell = index;
    const current = Number(arrayDetail.values[index]);
    const d = arrayDraftFor(selectedArray.name, index);
    arrayCurrent.textContent = valueWithUnit(current, selectedArray);
    arrayRequested.textContent = d ? valueWithUnit(d.requestedValue, selectedArray) : '—';
    arrayEffective.textContent = d ? valueWithUnit(d.effectiveValue, selectedArray) : '—';
    arrayInput.disabled = false;
    arrayApplyEdit.disabled = false;
    arrayWrite.hidden = !d;
    arrayWrite.disabled = !d;
    arrayInput.value = d ? String(d.requestedValue) : String(current);
    let coordinate = 'Cell #' + index;
    if (selectedSurface?.kind === 'table' && Array.isArray(arrayXDetail?.values) && Array.isArray(arrayYDetail?.values)) {
      const xCount = Number(selectedSurface.xCount);
      const x = xCount > 0 ? index % xCount : -1;
      const y = xCount > 0 ? Math.floor(index / xCount) : -1;
      if (x >= 0 && y >= 0 && x < arrayXDetail.values.length && y < arrayYDetail.values.length) {
        coordinate = 'X ' + valueWithUnit(arrayXDetail.values[x], arrayXDetail) + ' • Y ' + valueWithUnit(arrayYDetail.values[y], arrayYDetail);
      }
    } else if (selectedSurface?.kind === 'curve' && Array.isArray(arrayXDetail?.values) && index < arrayXDetail.values.length) {
      coordinate = 'X ' + valueWithUnit(arrayXDetail.values[index], arrayXDetail);
    }
    const selectionText = selectedArrayCells.size > 1 ? ' • ' + selectedArrayCells.size + ' cells selected' : '';
    arrayStatus.className = 't4tw-status' + (d ? ' good' : '');
    arrayStatus.textContent = d
      ? coordinate + selectionText + ' • PENDING EDIT • ' + d.changedBytes + ' encoded byte(s) will change • not yet written to ECU'
      : coordinate + selectionText + ' selected. Edit this cell or use Apply to selection.';
    updateArraySelectionControls();
    renderArrayGrid();
    if (focusInput) setTimeout(() => arrayInput.focus(), 0);
  }


  function getSemanticArrayDetail(name) {
    if (!name) return {status:'error', reason:'Semantic array name is missing'};
    try {
      const raw = window.EpicDashAndroid?.getTuningArrayDetailJson?.(name);
      return raw ? JSON.parse(raw) : {status:'error', reason:'Native array detail bridge unavailable'};
    } catch (error) {
      return {status:'error', reason:String(error && error.message || error)};
    }
  }

  function currentArrayIdentity(detail) {
    return detail && detail.status === 'ready' &&
      detail.generation === workspace?.generation &&
      detail.profileFingerprint === workspace?.profileFingerprint &&
      detail.tuneFingerprint === workspace?.tuneFingerprint;
  }

  function prepareArraySurface(item, allowReadOnly = false) {
    if (item?.writeAllowed === false && !allowReadOnly) {
      meta.textContent = item.writeBlockReason || 'Current INI conditions block this table/curve.';
      return false;
    }
    const targetName = String(item.targetArray || item.name || '');
    const targetItem = workspaceArray(targetName);
    if (!targetName || !targetItem) {
      meta.textContent = 'Logical table/curve target array is unavailable in the current TuneSnapshot.';
      return false;
    }

    const detail = getSemanticArrayDetail(targetName);
    const xDetail = getSemanticArrayDetail(String(item.xBins || ''));
    const yDetail = item.kind === 'table'
      ? getSemanticArrayDetail(String(item.yBins || ''))
      : detail;

    if (!currentArrayIdentity(detail) || !currentArrayIdentity(xDetail) || !currentArrayIdentity(yDetail)) {
      const failure = [detail, xDetail, yDetail].find(value => !currentArrayIdentity(value));
      meta.textContent = failure?.reason || 'Table/curve axis identity is stale or unavailable; refresh the workspace.';
      return false;
    }

    if (item.kind === 'table') {
      const dims = Array.isArray(detail.dimensions) ? detail.dimensions.map(Number) : [];
      if (dims.length !== 2 ||
          dims[0] !== Number(item.xCount) ||
          dims[1] !== Number(item.yCount) ||
          xDetail.values?.length !== Number(item.xCount) ||
          yDetail.values?.length !== Number(item.yCount)) {
        meta.textContent = 'Table axis/bin dimensions no longer match the current logical table definition.';
        return false;
      }
    } else if (item.kind === 'curve' &&
               (xDetail.values?.length !== detail.values?.length || detail.values?.length !== Number(item.pointCount))) {
      meta.textContent = 'Curve X/Y point counts no longer match the current logical curve definition.';
      return false;
    }

    selectedSurface = item;
    selectedArray = Object.assign({}, targetItem, {
      name:targetName,
      surfaceKind:String(item.kind || 'array'),
      surfaceId:String(item.id || targetName),
      surfaceTitle:String(item.title || item.id || targetName)
    });
    arrayDetail = detail;
    arrayXDetail = xDetail;
    arrayYDetail = yDetail;
    selectedArrayCell = -1;
    selectedArrayAnchor = -1;
    selectedArrayCells = new Set();
    arrayRangeSelectArmed = false;
    if (regionOperation) regionOperation.value = 'set';
    if (regionValue) regionValue.value = '';
    return true;
  }

  function openArrayEditor(item) {
    if (!prepareArraySurface(item)) return;
    arrayTitle.textContent = selectedArray.surfaceTitle;
    const dims = Array.isArray(arrayDetail.dimensions) ? arrayDetail.dimensions.join(' × ') : '—';
    const axisText = item.kind === 'table'
      ? ('X ' + item.xBins + ' • Y ' + item.yBins)
      : ('X ' + item.xBins + ' • Y ' + selectedArray.name);
    arrayMeta.textContent = String(item.kind || 'array').toUpperCase() + ' • ' + dims + ' • ' + arrayDetail.elementCount + ' cells • ' + axisText + ' • PENDING EDITS';
    arrayCurrent.textContent = '—';
    arrayRequested.textContent = '—';
    arrayEffective.textContent = '—';
    arrayInput.value = '';
    arrayInput.disabled = true;
    arrayApplyEdit.disabled = true;
    arrayWrite.hidden = true;
    arrayWrite.disabled = true;
    arrayStatus.className = 't4tw-status';
    arrayStatus.textContent = 'Select a cell, use Rectangle / range for touch selection, click an axis for a row/column, or Select all.';
    updateArraySelectionControls();
    renderArrayGrid();
    arrayEditor.hidden = false;
  }

  function stageArrayCellEdit() {
    if (!workspace || !selectedArray || !arrayDetail || selectedArrayCell < 0) return;
    const requested = Number(arrayInput.value);
    if (!Number.isFinite(requested)) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Enter a finite engineering value.';
      return;
    }
    let result;
    try {
      const raw = window.EpicDashAndroid?.previewTuningArrayCellJson?.(selectedArray.name, selectedArrayCell, requested);
      result = raw ? JSON.parse(raw) : {status:'error', reason:'Native array edit-validation bridge unavailable'};
    } catch (error) {
      result = {status:'error', reason:String(error && error.message || error)};
    }
    if (isEncodedArrayNoOp(result)) {
      const id = arrayDraftId(selectedArray.name, selectedArrayCell);
      const hadPending = !!arrayDraft[id];
      delete arrayDraft[id];
      saveArrayDraft();
      arrayWrite.hidden = true;
      arrayWrite.disabled = true;
      arrayRequested.textContent = valueWithUnit(requested, selectedArray);
      arrayEffective.textContent = valueWithUnit(arrayDetail.values[selectedArrayCell], selectedArray);
      arrayStatus.className = 't4tw-status';
      arrayStatus.textContent = 'No encoded ECU change for cell #' + selectedArrayCell +
        (hadPending ? '; the previous pending edit for this cell was removed.' : '.');
      updateChangeCount();
      renderArrayGrid();
      render();
      return;
    }
    if (result.status !== 'ready' || result.writeEligible !== true) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = result.reason || 'Array-cell edit rejected.';
      arrayRequested.textContent = valueWithUnit(requested, selectedArray);
      arrayEffective.textContent = '—';
      return;
    }
    if (result.generation !== workspace.generation || result.profileFingerprint !== workspace.profileFingerprint || result.tuneFingerprint !== workspace.tuneFingerprint) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Array edit identity is stale; Read ECU or refresh before continuing.';
      return;
    }
    const id = arrayDraftId(selectedArray.name, selectedArrayCell);
    arrayDraft[id] = {
      id:id, kind:'arrayCell', arrayName:selectedArray.name, cellIndex:selectedArrayCell,
      currentValue:Number(result.currentValue), requestedValue:Number(result.requestedValue), effectiveValue:Number(result.effectiveValue),
      unit:String(result.unit || selectedArray.unit || ''), digits:Number(selectedArray.digits),
      changedBytes:Number(result.changedBytes || 0), generation:Number(result.generation),
      profileFingerprint:String(result.profileFingerprint || ''), tuneFingerprint:String(result.tuneFingerprint || ''),
      savedAt:Date.now()
    };
    saveArrayDraft();
    arrayWrite.hidden = false;
    arrayWrite.disabled = false;
    arrayRequested.textContent = valueWithUnit(result.requestedValue, selectedArray);
    arrayEffective.textContent = valueWithUnit(result.effectiveValue, selectedArray);
    arrayStatus.className = 't4tw-status good';
    arrayStatus.textContent = 'PENDING CELL #' + selectedArrayCell + ' • ' + result.changedBytes + ' encoded byte(s) will change • use Write this cell or Changes → WRITE ALL';
    updateChangeCount();
    renderArrayGrid();
    render();
  }

  function stageArraySelectionEdit() {
    if (!workspace || !selectedArray || !arrayDetail) return;
    const indices = selectedArrayIndices();
    if (!indices.length) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Select one or more cells first.';
      return;
    }
    if (indices.length > MAX_TABLE_SELECTION) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Selection exceeds the native 2048-change batch limit.';
      return;
    }
    const operand = Number(regionValue?.value);
    const mode = String(regionOperation?.value || 'set');
    if (!Number.isFinite(operand)) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Enter a finite selection edit value.';
      return;
    }

    const previews = [];
    try {
      for (const index of indices) {
        const base = arrayBaseValue(index);
        const requested = selectionOperationValue(mode, base, operand);
        if (!Number.isFinite(requested)) throw new Error('Selection operation produced a non-finite value at cell #' + index);
        const raw = window.EpicDashAndroid?.previewTuningArrayCellJson?.(selectedArray.name, index, requested);
        const result = raw ? JSON.parse(raw) : {status:'error', reason:'Native array edit-validation bridge unavailable'};
        if (isEncodedArrayNoOp(result)) {
          previews.push({index, noOp:true});
          continue;
        }
        if (result.status !== 'ready' || result.writeEligible !== true) throw new Error('Cell #' + index + ': ' + (result.reason || 'edit rejected'));
        if (result.generation !== workspace.generation ||
            result.profileFingerprint !== workspace.profileFingerprint ||
            result.tuneFingerprint !== workspace.tuneFingerprint) {
          throw new Error('Cell #' + index + ': edit identity is stale; Read ECU or refresh before continuing');
        }
        previews.push({index, result, noOp:false});
      }
    } catch (error) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = String(error && error.message || error);
      return;
    }

    let staged = 0, cleared = 0, changedBytes = 0;
    for (const preview of previews) {
      const index = preview.index, result = preview.result;
      const id = arrayDraftId(selectedArray.name, index);
      if (preview.noOp === true) {
        if (arrayDraft[id]) {
          delete arrayDraft[id];
          cleared++;
        }
        continue;
      }
      arrayDraft[id] = {
        id:id, kind:'arrayCell', arrayName:selectedArray.name, cellIndex:index,
        currentValue:Number(result.currentValue), requestedValue:Number(result.requestedValue), effectiveValue:Number(result.effectiveValue),
        unit:String(result.unit || selectedArray.unit || ''), digits:Number(selectedArray.digits),
        changedBytes:Number(result.changedBytes || 0), generation:Number(result.generation),
        profileFingerprint:String(result.profileFingerprint || ''), tuneFingerprint:String(result.tuneFingerprint || ''),
        savedAt:Date.now()
      };
      staged++;
      changedBytes += Number(result.changedBytes || 0);
    }
    saveArrayDraft();
    updateChangeCount();
    if (selectedArrayCell >= 0) refreshArrayCellInspector(selectedArrayCell, false);
    else renderArrayGrid();
    render();
    arrayStatus.className = staged > 0 ? 't4tw-status good' : 't4tw-status';
    const omitted = previews.filter(preview => preview.noOp === true).length;
    arrayStatus.textContent = staged > 0
      ? ('Validated ' + staged + ' changed cell(s) • sending to ECU RAM for exact verification…')
      : ('Selection produced no encoded ECU change' +
         (omitted ? '; ' + omitted + ' no-op cell(s) omitted' : '') +
         (cleared ? '; ' + cleared + ' pending cell edit(s) removed' : '') + '.');
    if (staged > 0) {
      const changes = previews.filter(preview => preview.noOp !== true)
        .map(preview => currentArrayWrite(selectedArray.name, preview.index))
        .filter(Boolean);
      queueSemanticWrites(changes, arrayStatus);
    }
  }


  function renderChanges() {
    changesList.textContent = '';
    const scalarEntries = Object.values(draft).filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint)
      .map(entry => Object.assign({kind:'scalar'}, entry));
    const bitEntries = Object.values(bitDraft).filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint);
    const arrayEntries = Object.values(arrayDraft).filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint);
    const entries = scalarEntries.concat(bitEntries, arrayEntries)
      .sort((a,b) => String(a.name || a.arrayName).localeCompare(String(b.name || b.arrayName)) || Number(a.cellIndex || 0) - Number(b.cellIndex || 0));
    if (!entries.length) {
      changesList.innerHTML = '<div class="t4tw-empty">No pending edits for this exact ECU tune snapshot.</div>';
      return;
    }
    for (const entry of entries) {
      const isArray = entry.kind === 'arrayCell';
      const isBit = entry.kind === 'bitField';
      const item = isArray ? (workspaceArray(entry.arrayName) || entry) : isBit ? (workspaceBitField(entry.name) || entry) : (workspaceScalar(entry.name) || entry);
      const row = document.createElement('div');
      row.className = 't4tw-change';
      const name = document.createElement('div');
      name.className = 't4tw-change-name';
      name.textContent = isArray ? (entry.arrayName + '[' + entry.cellIndex + ']') : entry.name;
      const values = document.createElement('div');
      values.textContent = isBit
        ? (String(entry.currentLabel ?? entry.currentValue) + ' → ' + String(entry.effectiveLabel ?? entry.effectiveValue))
        : (valueWithUnit(entry.currentValue, item) + ' → ' + valueWithUnit(entry.effectiveValue, item));
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 't4tw-btn';
      remove.textContent = 'Remove';
      remove.onclick = () => {
        if (isArray) {
          delete arrayDraft[entry.id || arrayDraftId(entry.arrayName, entry.cellIndex)];
          saveArrayDraft();
        } else if (isBit) {
          delete bitDraft[entry.name];
          saveBitDraft();
        } else {
          delete draft[entry.name];
          saveDraft();
        }
        updateChangeCount();
        renderChanges();
        render();
        renderArrayGrid();
      };
      row.append(name, values, remove);
      changesList.appendChild(row);
    }
  }

  function collectDialogRouteTargets(dialogId, kind, stack = [], depth = 0) {
    const results = [];
    const pushUnique = item => {
      if (!item) return;
      const key = kind === 'scalar' ? String(item.name || '') : String(item.id || '');
      if (!key || results.some(existing => (kind === 'scalar' ? existing.name : existing.id) === key)) return;
      results.push(item);
    };
    if (kind === 'surface') {
      const direct = workspaceSurface(dialogId);
      if (direct) {
        pushUnique(direct);
        return results;
      }
    }
    const dialog = workspaceDialog(dialogId);
    if (!dialog || stack.includes(dialogId) || depth > 8) return results;
    const nextStack = stack.concat(dialogId);
    for (const entry of (dialog.entries || [])) {
      if (!conditionVisible(entry)) continue;
      const target = String(entry.target || '');
      if (!target) continue;
      if (entry.kind === 'field') {
        if (kind === 'scalar') pushUnique(workspaceScalar(target));
        else pushUnique(workspaceSurface(target));
      } else if (entry.kind === 'panel') {
        if (kind === 'surface') {
          const surface = workspaceSurface(target);
          if (surface) {
            pushUnique(surface);
            continue;
          }
        }
        collectDialogRouteTargets(target, kind, nextStack, depth + 1).forEach(pushUnique);
      }
    }
    return results;
  }

  function buildIniGroupedRoutes(kind) {
    const cached = groupedRoutesCache.get(kind);
    if (cached) return cached;

    const blueprintKey = String(workspace?.profileFingerprint || '') + ':' + kind;
    let blueprint = groupedRouteBlueprintCache.get(blueprintKey);
    if (!blueprint) {
      const routes = [];
      const coveredKeys = new Set();
      for (const menuItem of (workspace?.menuItems || [])) {
        if (!conditionVisible(menuItem)) continue;
        const items = collectDialogRouteTargets(String(menuItem.dialogId || ''), kind);
        if (!items.length) continue;
        const itemKeys = items
          .map(item => kind === 'scalar' ? String(item.name || '') : String(item.id || ''))
          .filter(Boolean);
        if (!itemKeys.length) continue;
        itemKeys.forEach(key => coveredKeys.add(key));
        routes.push({
          menu: cleanMenuLabel(menuItem.menu) || 'Other',
          group: cleanMenuLabel(menuItem.group),
          title: String(menuItem.title || menuItem.dialogId || ''),
          dialogId: String(menuItem.dialogId || ''),
          conditions: Array.isArray(menuItem.conditions) ? menuItem.conditions : [],
          conditionState: menuItem.conditionState || null,
          itemKeys
        });
      }
      blueprint = {routes, coveredKeys:Array.from(coveredKeys)};
      groupedRouteBlueprintCache.set(blueprintKey, blueprint);
    }

    const resolve = kind === 'scalar' ? workspaceScalar : workspaceSurface;
    const routes = blueprint.routes
      .map(route => Object.assign({}, route, {
        items: route.itemKeys.map(key => resolve(key)).filter(Boolean)
      }))
      .filter(route => route.items.length);
    const result = {routes, covered:new Set(blueprint.coveredKeys)};
    groupedRoutesCache.set(kind, result);
    return result;
  }

  function routeSearchText(route) {
    return [route?.menu, route?.group, route?.title, route?.dialogId]
      .map(value => String(value || '').toLowerCase()).join(' ');
  }

  function scalarMatchesView(item, q, route = null) {
    const pending = !!draftFor(item.name);
    if (activeFilter === 'favorites' && !favorites.has(item.name)) return false;
    if (activeFilter === 'pending' && !pending) return false;
    if (!q) return true;
    if (route && routeSearchText(route).includes(q)) return true;
    return String(item.name || '').toLowerCase().includes(q) ||
      String(item.unit || '').toLowerCase().includes(q);
  }

  function surfaceFavoriteKey(item) {
    return 'surface:' + String(item.kind || '') + ':' + String(item.id || '');
  }

  function surfaceMatchesView(item, q, route = null) {
    const target = String(item.targetArray || '');
    const pending = arrayHasDraft(target);
    const key = surfaceFavoriteKey(item);
    if (activeFilter === 'favorites' && !favorites.has(key)) return false;
    if (activeFilter === 'pending' && !pending) return false;
    if (!q) return true;
    if (route && routeSearchText(route).includes(q)) return true;
    return String(item.title || '').toLowerCase().includes(q) ||
      String(item.id || '').toLowerCase().includes(q) ||
      target.toLowerCase().includes(q) ||
      String(item.unit || '').toLowerCase().includes(q);
  }

  function appendIniRouteHeader(route, state, container = list) {
    if (route.menu !== state.menu) {
      const heading = document.createElement('div');
      heading.className = 't4tw-settings-heading';
      heading.textContent = route.menu || 'Other';
      container.appendChild(heading);
      state.menu = route.menu;
      state.group = null;
    }
    if (route.group && route.group !== state.group) {
      const group = document.createElement('div');
      group.className = 't4tw-settings-group';
      group.textContent = route.group;
      container.appendChild(group);
      state.group = route.group;
    }
    const routeTitle = document.createElement('div');
    routeTitle.className = 't4tw-route-title';
    routeTitle.textContent = route.title || route.dialogId || 'INI group';
    const key = document.createElement('small');
    key.textContent = route.dialogId || '';
    routeTitle.appendChild(key);
    const condition = makeConditionNode(route);
    if (condition) routeTitle.appendChild(condition);
    container.appendChild(routeTitle);
  }

  function appendUngroupedHeader(label, container = list) {
    const heading = document.createElement('div');
    heading.className = 't4tw-settings-heading t4tw-ungrouped';
    heading.textContent = 'Ungrouped';
    container.appendChild(heading);
    const group = document.createElement('div');
    group.className = 't4tw-settings-group';
    group.textContent = label + ' not referenced by the parsed INI menu/dialog tree';
    container.appendChild(group);
  }

  function appendScalarRow(item, container = list) {
    const d = draftFor(item.name);
    const row = document.createElement('div');
    row.className = 't4tw-row' + (d ? ' pending' : '') + (item.writeAllowed === false ? ' blocked' : '');
    row.onclick = () => openEditor(item);
    const star = document.createElement('button');
    star.type = 'button';
    star.className = 't4tw-star' + (favorites.has(item.name) ? ' on' : '');
    star.textContent = favorites.has(item.name) ? '★' : '☆';
    star.setAttribute('aria-label', 'Toggle Quick Tuning favorite');
    star.onclick = event => {
      event.stopPropagation();
      if (favorites.has(item.name)) favorites.delete(item.name); else favorites.add(item.name);
      saveFavorites(); render();
    };
    const name = document.createElement('div');
    name.className = 't4tw-name';
    name.textContent = item.name;
    if (d) {
      const tag = document.createElement('span');
      tag.className = 't4tw-tag';
      tag.textContent = 'SIM';
      name.appendChild(tag);
    }
    const value = document.createElement('div');
    value.className = 't4tw-value';
    if (d) {
      value.innerHTML = '<span>' + formatNumber(item.value, item) + '</span><span class="t4tw-arrow"> → </span><span class="t4tw-simvalue">' + formatNumber(d.effectiveValue, item) + '</span>';
    } else {
      value.textContent = formatNumber(item.value, item);
    }
    if (item.unit) {
      const unit = document.createElement('span');
      unit.className = 't4tw-unit';
      unit.textContent = item.unit;
      value.appendChild(unit);
    }
    const range = document.createElement('div');
    range.className = 't4tw-range';
    range.textContent = item.writeAllowed === false
      ? (item.writeBlockReason || 'blocked by INI condition')
      : rangeText(item);
    row.append(star, name, value, range);
    container.appendChild(row);
  }

  function appendSurfaceRow(item, container = list) {
    const target = String(item.targetArray || '');
    const pending = arrayHasDraft(target);
    const key = surfaceFavoriteKey(item);
    const row = document.createElement('div');
    row.className = 't4tw-arrayrow' + (pending ? ' pending' : '') + (item.writeAllowed === false ? ' blocked' : '');
    row.onclick = () => openArrayEditor(item);
    const star = document.createElement('button');
    star.type = 'button';
    star.className = 't4tw-star' + (favorites.has(key) ? ' on' : '');
    star.textContent = favorites.has(key) ? '★' : '☆';
    star.setAttribute('aria-label', 'Toggle table/curve favorite');
    star.onclick = event => {
      event.stopPropagation();
      if (favorites.has(key)) favorites.delete(key); else favorites.add(key);
      saveFavorites(); render();
    };
    const name = document.createElement('div');
    name.className = 't4tw-name';
    name.textContent = item.title || item.id;
    const sub = document.createElement('div');
    sub.className = 'sub';
    sub.textContent = String(item.kind || '').toUpperCase() + ' • ' + item.id + ' • ' + target;
    name.appendChild(sub);
    if (pending) {
      const tag = document.createElement('span');
      tag.className = 't4tw-tag';
      tag.textContent = 'SIM';
      name.appendChild(tag);
    }
    const dims = document.createElement('div');
    dims.className = 't4tw-arraymeta';
    dims.textContent = item.kind === 'table'
      ? (String(item.xCount) + '×' + String(item.yCount))
      : (String(item.pointCount) + ' pt');
    const count = document.createElement('div');
    count.className = 't4tw-arraymeta';
    count.textContent = String(item.elementCount) + (item.kind === 'curve' ? ' points' : ' cells');
    const range = document.createElement('div');
    range.className = 't4tw-arraymeta';
    const targetMeta = workspaceArray(target) || item;
    range.textContent = item.writeAllowed === false
      ? (item.writeBlockReason || 'blocked by INI condition')
      : formatNumber(item.minimum, targetMeta) + ' … ' + formatNumber(item.maximum, targetMeta) + (item.unit ? ' ' + item.unit : '');
    row.append(star, name, dims, count, range);
    container.appendChild(row);
  }

  function renderGroupedScalars(q) {
    const grouping = buildIniGroupedRoutes('scalar');
    const routes = grouping.routes
      .map(route => Object.assign({}, route, {items: route.items.filter(item => scalarMatchesView(item, q, route))}))
      .filter(route => route.items.length);
    const ungrouped = (workspace.scalars || [])
      .filter(item => !grouping.covered.has(String(item.name)))
      .filter(item => scalarMatchesView(item, q));
    const fragment = document.createDocumentFragment();
    const state = {menu:null, group:null};
    let shown = 0;
    const MAX_ROWS = 250;
    const distinct = new Set();
    for (const route of routes) {
      if (shown >= MAX_ROWS) break;
      const available = route.items.slice(0, MAX_ROWS - shown);
      if (!available.length) continue;
      appendIniRouteHeader(route, state, fragment);
      for (const item of available) {
        appendScalarRow(item, fragment);
        distinct.add(String(item.name));
        shown++;
      }
    }
    if (shown < MAX_ROWS && ungrouped.length) {
      appendUngroupedHeader('Readable scalars', fragment);
      for (const item of ungrouped.slice(0, MAX_ROWS - shown)) {
        appendScalarRow(item, fragment);
        distinct.add(String(item.name));
        shown++;
      }
    }
    if (!shown) {
      const empty = document.createElement('div');
      empty.className = 't4tw-empty';
      empty.textContent = activeFilter === 'pending'
        ? 'No pending scalar edits match this INI grouping/search.'
        : activeFilter === 'favorites'
          ? 'No favorite scalars match this INI grouping/search.'
          : 'No readable current-INI scalars match this search.';
      fragment.appendChild(empty);
    }
    list.textContent = '';
    list.appendChild(fragment);
    const placements = routes.reduce((sum, route) => sum + route.items.length, 0) + ungrouped.length;
    const suffix = placements > shown ? ' • showing first ' + shown : '';
    const pendingCount = Object.keys(draft).filter(name => draftFor(name)).length;
    summary.textContent = distinct.size + ' unique matching scalars • ' + placements + ' INI placements • ' +
      routes.length + ' menu/dialog routes • ' + ungrouped.length + ' ungrouped • ' +
      workspace.readableScalars + ' readable / ' + workspace.totalProfileScalars + ' INI scalars • ' +
      workspace.skippedScalars + ' skipped • ' + workspace.ambiguousScalarNames + ' ambiguous • ' +
      pendingCount + ' pending' + suffix;
    updateChangeCount();
  }

  function surfaceHierarchy(kind, q) {
    const query = String(q || '').trim().toLowerCase();
    const systems = [];
    const systemMap = new Map();
    const covered = new Set();

    const addSurface = (systemName, categoryName, item) => {
      if (!item || String(item.kind || '') !== kind) return;
      const id = String(item.id || '');
      if (!id) return;
      let system = systemMap.get(systemName);
      if (!system) {
        system = {name:systemName, categories:[], categoryMap:new Map(), itemCount:0};
        systemMap.set(systemName, system);
        systems.push(system);
      }
      let category = system.categoryMap.get(categoryName);
      if (!category) {
        category = {name:categoryName, items:[], itemIds:new Set()};
        system.categoryMap.set(categoryName, category);
        system.categories.push(category);
      }
      if (category.itemIds.has(id)) return;
      category.itemIds.add(id);
      category.items.push(item);
      system.itemCount++;
      covered.add(id);
    };

    for (const menuItem of (workspace?.menuItems || [])) {
      if (!conditionVisible(menuItem)) continue;
      const systemName = presentationSystemName(menuItem.menu);
      const categoryName = presentationCategoryName(menuItem.menu, menuItem.group);
      const routeText = [menuItem.menu, menuItem.group, menuItem.title, menuItem.dialogId]
        .map(value => String(value || '').toLowerCase()).join(' ');
      for (const item of collectDialogRouteTargets(String(menuItem.dialogId || ''), 'surface')) {
        if (String(item.kind || '') !== kind) continue;
        const matches = !query || routeText.includes(query) ||
          [item.title, item.id, item.targetArray, item.unit]
            .some(value => String(value || '').toLowerCase().includes(query));
        if (matches) addSurface(systemName, categoryName, item);
      }
    }

    const catalog = kind === 'table'
      ? (Array.isArray(workspace?.tables) ? workspace.tables : [])
      : (Array.isArray(workspace?.curves) ? workspace.curves : []);
    for (const item of catalog) {
      const id = String(item.id || '');
      if (!id || covered.has(id)) continue;
      const matches = !query || [item.title, item.id, item.targetArray, item.unit]
        .some(value => String(value || '').toLowerCase().includes(query));
      if (matches) addSurface('Advanced', HIERARCHY_GENERAL_CATEGORY, item);
    }
    return sortPresentationSystems(systems);
  }

  function ensureTableHierarchySelection(systems) {
    if (!systems.length) {
      hierarchyTableId = '';
      return {system:null, category:null, table:null};
    }
    const system = systems.find(entry => entry.name === hierarchySystem) ||
      systems.find(entry => entry.name === 'Fuel') || systems[0];
    hierarchySystem = system.name;
    const category = system.categories.find(entry => entry.name === hierarchyCategory) ||
      (!hierarchyCategory ? system.categories.find(entry => /main fuel/i.test(entry.name)) : null) ||
      system.categories[0] || null;
    hierarchyCategory = category?.name || '';
    const tables = category?.items || [];
    const requested = tables.find(item => String(item.id || '') === hierarchyTableId) || null;
    const preferred = !hierarchyTableId ? tables.find(item => /\bve\b|volumetric/i.test(String(item.title || item.id || ''))) : null;
    const table = (requested && requested.writeAllowed !== false ? requested : null) ||
      (preferred && preferred.writeAllowed !== false ? preferred : null) ||
      tables.find(item => item.writeAllowed !== false) || requested || preferred || tables[0] || null;
    hierarchyTableId = table ? String(table.id || '') : '';
    return {system, category, table};
  }

  function tableSurfaceCurrent(item) {
    if (!item || selectedSurface?.kind !== 'table') return false;
    if (String(selectedSurface.id || selectedSurface.surfaceId || '') !== String(item.id || '')) return false;
    return currentArrayIdentity(arrayDetail) && currentArrayIdentity(arrayXDetail) && currentArrayIdentity(arrayYDetail);
  }

  function inlineTableSelectionCount() {
    if (selectedArrayCells.size) return selectedArrayCells.size;
    return selectedArrayCell >= 0 ? 1 : 0;
  }

  function refreshInlineTableInspector() {
    const selection = document.getElementById('t4twInlineTableSelection');
    const input = document.getElementById('t4twInlineTableValue');
    const status = document.getElementById('t4twInlineTableStatus');
    const zoom = document.getElementById('t4twInlineTableZoom');
    const range = document.getElementById('t4twInlineTableRange');
    if (range) {
      range.textContent = arrayRangeSelectArmed ? 'Range ✓' : 'Range';
      range.classList.toggle('active', arrayRangeSelectArmed);
    }
    if (zoom) zoom.textContent = Math.round(tableZoom * 100) + '%';
    if (!selection && !input && !status) return;

    const count = inlineTableSelectionCount();
    if (selection) selection.textContent = count + ' selected';
    if (input) {
      input.disabled = selectedArrayCell < 0 || selectedSurface?.writeAllowed === false;
      if (selectedArrayCell >= 0 && Array.isArray(arrayDetail?.values)) {
        const pending = arrayDraftFor(selectedArray?.name, selectedArrayCell);
        input.value = String(pending ? pending.requestedValue : arrayDetail.values[selectedArrayCell]);
      } else {
        input.value = '';
      }
    }

    if (!status) return;
    if (selectedArrayCell < 0) {
      status.textContent = 'Tap a cell, row/column heading, or use All to make a selection.';
      return;
    }
    const pending = arrayDraftFor(selectedArray?.name, selectedArrayCell);
    const xCount = Number(selectedSurface?.xCount || 0);
    const x = xCount > 0 ? selectedArrayCell % xCount : -1;
    const y = xCount > 0 ? Math.floor(selectedArrayCell / xCount) : -1;
    const coord = x >= 0 && y >= 0 &&
      Array.isArray(arrayXDetail?.values) && Array.isArray(arrayYDetail?.values)
      ? ('X ' + valueWithUnit(arrayXDetail.values[x], arrayXDetail) + ' • Y ' + valueWithUnit(arrayYDetail.values[y], arrayYDetail))
      : ('Cell #' + selectedArrayCell);
    status.textContent = coord + (count > 1 ? (' • ' + count + ' selected') : '') +
      (pending ? ' • pending edit' : '');
  }

  function ensureInlineTableSelection() {
    if (selectedArrayCells.size) return true;
    if (selectedArrayCell < 0) return false;
    selectedArrayCells = new Set([selectedArrayCell]);
    selectedArrayAnchor = selectedArrayCell;
    return true;
  }

  function applyInlineTableSelection(mode, operand) {
    if (selectedSurface?.writeAllowed === false) {
      const status = document.getElementById('t4twInlineTableStatus');
      if (status) status.textContent = selectedSurface.writeBlockReason || 'Current INI conditions make this table read-only.';
      return;
    }
    if (!ensureInlineTableSelection()) {
      const status = document.getElementById('t4twInlineTableStatus');
      if (status) status.textContent = 'Select one or more cells first.';
      return;
    }
    if (!Number.isFinite(Number(operand))) {
      const status = document.getElementById('t4twInlineTableStatus');
      if (status) status.textContent = 'Enter a finite value.';
      return;
    }
    regionOperation.value = mode;
    regionValue.value = String(operand);
    stageArraySelectionEdit();
  }

  function setInlineTableZoom(nextZoom) {
    tableZoom = Math.max(0.4, Math.min(2.4, Number(nextZoom) || 1));
    const grid = document.getElementById('t4twInlineTableGrid');
    if (grid) {
      const cell = Math.round(22 * tableZoom);
      grid.style.setProperty('--t4tw-inline-cell', cell + 'px');
    }
    refreshInlineTableInspector();
  }

  function fitInlineTable() {
    const viewport = document.getElementById('t4twInlineTableViewport');
    const grid = document.getElementById('t4twInlineTableGrid');
    const xCount = Number(selectedSurface?.xCount || 0);
    const yCount = Number(selectedSurface?.yCount || 0);
    if (!viewport || !grid || xCount <= 0 || yCount <= 0) return;
    const widthCell = Math.max(9, (viewport.clientWidth - 54) / xCount);
    const heightCell = Math.max(9, (viewport.clientHeight - 24) / yCount);
    const cell = Math.max(9, Math.min(34, widthCell, heightCell));
    tableZoom = Math.max(0.4, Math.min(2.4, cell / 22));
    grid.style.setProperty('--t4tw-inline-cell', Math.round(22 * tableZoom) + 'px');
    refreshInlineTableInspector();
  }

  function inlineMenu(label, items) {
    const details = document.createElement('details');
    details.className = 't4tw-menu';
    const summaryNode = document.createElement('summary');
    summaryNode.className = 't4tw-btn';
    summaryNode.textContent = label + '⌄';
    const panel = document.createElement('div');
    panel.className = 't4tw-menu-panel';
    for (const item of items) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 't4tw-btn';
      button.textContent = item.label;
      button.disabled = item.disabled === true;
      button.onclick = event => {
        event.preventDefault();
        if (item.action) item.action();
        details.open = false;
      };
      panel.appendChild(button);
    }
    details.append(summaryNode, panel);
    return details;
  }

  function currentTableDraftChanges() {
    if (!selectedArray?.name) return [];
    return Object.values(arrayDraft)
      .filter(entry => entry &&
        entry.arrayName === selectedArray.name &&
        entry.tuneFingerprint === workspace?.tuneFingerprint)
      .map(entry => currentArrayWrite(entry.arrayName, entry.cellIndex))
      .filter(Boolean);
  }

  function writeCurrentTableDrafts() {
    const changes = currentTableDraftChanges();
    const status = document.getElementById('t4twInlineTableStatus');
    if (!changes.length) {
      if (status) status.textContent = 'No pending edits for this table.';
      return;
    }
    if (queueSemanticWrites(changes, status)) {
      if (status) status.textContent = changes.length + ' table cell edit(s) queued for verified ECU RAM write.';
    }
  }

  function renderSystemRail(systems, selectedName, onSelect) {
    const rail = document.createElement('aside');
    rail.className = 't4tw-system-rail';
    const body = document.createElement('div');
    body.className = 't4tw-system-rail-list';
    for (const system of systems) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 't4tw-system-rail-item' + (system.name === selectedName ? ' active' : '');
      const icon = document.createElement('span');
      icon.className = 't4tw-system-rail-icon';
      icon.innerHTML = systemIconSvg(system.name);
      const label = document.createElement('span');
      label.textContent = system.name;
      button.append(icon, label);
      button.onclick = () => onSelect(system);
      body.appendChild(button);
    }
    rail.appendChild(body);
    return rail;
  }


  function renderTablesMode(q) {
    const systems = surfaceHierarchy('table', q);
    const selected = ensureTableHierarchySelection(systems);
    const fragment = document.createDocumentFragment();

    if (!systems.length || !selected.table) {
      const empty = document.createElement('div');
      empty.className = 't4tw-empty';
      empty.textContent = q ? 'No current-INI tables match this search.' : 'No current-INI tables are available.';
      fragment.appendChild(empty);
      list.textContent = '';
      list.appendChild(fragment);
      summary.textContent = '0 Tables';
      updateChangeCount();
      return;
    }

    if (!tableSurfaceCurrent(selected.table)) {
      tableFitPending = true;
      if (!prepareArraySurface(selected.table, true)) {
        const empty = document.createElement('div');
        empty.className = 't4tw-empty';
        empty.textContent = meta.textContent || 'Selected table could not be loaded.';
        list.textContent = '';
        list.appendChild(empty);
        return;
      }
    }

    const shell = document.createElement('div');
    shell.className = 't4tw-table-shell';
    shell.dataset.uiContract = 'responsive-tables-v1';

    const rail = renderSystemRail(systems, selected.system?.name || '', system => {
      hierarchySystem = system.name;
      hierarchyCategory = system.categories[0]?.name || '';
      hierarchyTableId = '';
      tableFitPending = true;
      render();
    });

    const mainPane = document.createElement('div');
    mainPane.className = 't4tw-table-main';

    const selectors = document.createElement('div');
    selectors.className = 't4tw-hierarchy-selectors';
    selectors.dataset.uiContract = 'responsive-hierarchy-v1';

    const systemSelect = hierarchySelect(
      'System',
      selected.system?.name || '',
      systems.map(system => ({value:system.name, label:system.name})),
      value => {
        hierarchySystem = value;
        const next = systems.find(system => system.name === value);
        hierarchyCategory = next?.categories?.[0]?.name || '';
        hierarchyTableId = '';
        tableFitPending = true;
        render();
      }
    );

    const categories = selected.system?.categories || [];
    const categorySelect = hierarchySelect(
      'Category',
      selected.category?.name || '',
      categories.map(category => ({value:category.name, label:category.name})),
      value => {
        hierarchyCategory = value;
        hierarchyTableId = '';
        tableFitPending = true;
        render();
      }
    );

    const tables = selected.category?.items || [];
    const tableSelect = hierarchySelect(
      'Table',
      selected.table?.id || '',
      tables.map(item => ({
        value:String(item.id || ''),
        label:item.title || item.id,
        disabled:item.writeAllowed === false
      })),
      value => {
        hierarchyTableId = String(value || '');
        tableFitPending = true;
        render();
      }
    );

    selectors.append(
      systemSelect,
      hierarchyChevron('system'),
      categorySelect,
      hierarchyChevron('category'),
      tableSelect
    );
    appendResponsiveNav(mainPane, selectors);

    const head = document.createElement('div');
    head.className = 't4tw-table-head';
    const titleBlock = document.createElement('div');
    titleBlock.className = 't4tw-table-title-block';
    const title = document.createElement('div');
    title.className = 't4tw-table-title';
    title.textContent = selected.table.title || selected.table.id;
    const sub = document.createElement('div');
    sub.className = 't4tw-table-sub';
    sub.textContent = [selected.system?.name, selected.category?.name,
      Number(selected.table.xCount) + '×' + Number(selected.table.yCount),
      'X ' + String(selected.table.xBins || ''), 'Y ' + String(selected.table.yBins || '')]
      .filter(Boolean).join(' • ');
    titleBlock.append(title, sub);

    const viewControls = document.createElement('div');
    viewControls.className = 't4tw-table-view-controls';
    const fit = document.createElement('button');
    fit.type = 'button';
    fit.className = 't4tw-btn';
    fit.textContent = 'Fit';
    fit.onclick = fitInlineTable;
    const zoomOut = document.createElement('button');
    zoomOut.type = 'button';
    zoomOut.className = 't4tw-btn';
    zoomOut.textContent = '−';
    zoomOut.onclick = () => setInlineTableZoom(tableZoom - 0.1);
    const zoomLabel = document.createElement('span');
    zoomLabel.id = 't4twInlineTableZoom';
    zoomLabel.className = 't4tw-table-selection';
    zoomLabel.textContent = Math.round(tableZoom * 100) + '%';
    const zoomIn = document.createElement('button');
    zoomIn.type = 'button';
    zoomIn.className = 't4tw-btn';
    zoomIn.textContent = '+';
    zoomIn.onclick = () => setInlineTableZoom(tableZoom + 0.1);
    const view2d = document.createElement('button');
    view2d.type = 'button';
    view2d.className = 't4tw-btn active';
    view2d.textContent = '2D';
    const view3d = document.createElement('button');
    view3d.type = 'button';
    view3d.className = 't4tw-btn';
    view3d.textContent = '3D';
    view3d.disabled = true;
    view3d.title = '3D table rendering is not implemented in this slice.';
    viewControls.append(fit, zoomOut, zoomLabel, zoomIn, view2d, view3d);
    head.append(titleBlock, viewControls);
    mainPane.appendChild(head);

    const viewport = document.createElement('div');
    viewport.className = 't4tw-table-viewport';
    viewport.id = 't4twInlineTableViewport';
    const grid = document.createElement('div');
    grid.className = 't4tw-table-grid';
    grid.id = 't4twInlineTableGrid';
    viewport.appendChild(grid);
    mainPane.appendChild(viewport);

    const editbar = document.createElement('div');
    editbar.className = 't4tw-table-editbar';
    const selection = document.createElement('span');
    selection.className = 't4tw-table-selection';
    selection.id = 't4twInlineTableSelection';
    selection.textContent = inlineTableSelectionCount() + ' selected';
    const delta = value => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 't4tw-btn';
      button.textContent = value > 0 ? ('+' + value) : String(value);
      button.disabled = selected.table.writeAllowed === false;
      button.onclick = () => applyInlineTableSelection(value >= 0 ? 'add' : 'subtract', Math.abs(value));
      return button;
    };
    const valueInput = document.createElement('input');
    valueInput.id = 't4twInlineTableValue';
    valueInput.className = 't4tw-table-value-input';
    valueInput.inputMode = 'decimal';
    valueInput.placeholder = 'Value';
    valueInput.disabled = selected.table.writeAllowed === false;
    const setButton = document.createElement('button');
    setButton.type = 'button';
    setButton.className = 't4tw-btn active';
    setButton.textContent = 'Set';
    setButton.disabled = selected.table.writeAllowed === false;
    setButton.onclick = () => applyInlineTableSelection('set', Number(valueInput.value));
    valueInput.addEventListener('keydown', event => {
      if (event.key === 'Enter') applyInlineTableSelection('set', Number(valueInput.value));
    });
    const undo = document.createElement('button');
    undo.type = 'button';
    undo.className = 't4tw-btn';
    undo.textContent = '↶ Undo';
    undo.disabled = true;
    undo.title = 'Session-level undo will be added after live-write parity is physically accepted.';
    const redo = document.createElement('button');
    redo.type = 'button';
    redo.className = 't4tw-btn';
    redo.textContent = '↷ Redo';
    redo.disabled = true;

    const selectMenu = inlineMenu('Select ', [
      {label:'Rectangle / range', action:()=>{if(selectedArrayCell>=0){arrayRangeSelectArmed=!arrayRangeSelectArmed;if(arrayRangeSelectArmed)selectedArrayAnchor=selectedArrayCell;refreshInlineTableInspector();}}},
      {label:'Row', action:()=>{if(selectedArrayCell>=0)selectArrayRow(Math.floor(selectedArrayCell/Number(selectedSurface?.xCount||1)));}},
      {label:'Column', action:()=>{if(selectedArrayCell>=0)selectArrayColumn(selectedArrayCell%Number(selectedSurface?.xCount||1));}},
      {label:'All', action:selectAllArrayCells},
      {label:'Clear', action:()=>{arrayRangeSelectArmed=false;clearArraySelection();refreshInlineTableInspector();}}
    ]);
    const toolsMenu = inlineMenu('Tools ', [
      {label:'Smooth', disabled:true},
      {label:'Interpolate', disabled:true},
      {label:'Copy', disabled:true},
      {label:'Paste', disabled:true}
    ]);

    editbar.append(selection, delta(-10), delta(-1), delta(1), delta(10), valueInput, setButton, undo, redo, selectMenu, toolsMenu);
    mainPane.appendChild(editbar);

    const status = document.createElement('div');
    status.id = 't4twInlineTableStatus';
    status.className = 't4tw-table-status';
    mainPane.appendChild(status);

    shell.append(rail, mainPane);
    fragment.appendChild(shell);
    list.textContent = '';
    list.appendChild(fragment);
    renderArrayGridInto(grid, true);
    refreshInlineTableInspector();

    if (tableFitPending) {
      tableFitPending = false;
      setTimeout(fitInlineTable, 0);
    }

    const pendingCount = Object.values(arrayDraft)
      .filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint).length;
    summary.textContent = Number(workspace.readableTables || 0) + ' readable table(s) • ' +
      pendingCount + ' pending table/curve cell edit(s) • INI ' +
      String(workspace.iniCompatibility?.state || 'amber').toUpperCase();
    updateChangeCount();
  }

  function ensureCurveHierarchySelection(systems) {
    if (!systems.length) {
      hierarchyCurveId = '';
      return {system:null, category:null, curve:null};
    }
    const system = systems.find(entry => entry.name === hierarchySystem) ||
      systems.find(entry => entry.name === 'Fuel') || systems[0];
    hierarchySystem = system.name;
    const category = system.categories.find(entry => entry.name === hierarchyCategory) ||
      (!hierarchyCategory ? system.categories.find(entry => /transient/i.test(entry.name)) : null) ||
      system.categories[0] || null;
    hierarchyCategory = category?.name || '';
    const curves = category?.items || [];
    const requested = curves.find(item => String(item.id || '') === hierarchyCurveId) || null;
    const preferred = !hierarchyCurveId ? curves.find(item => /tps.*ae|accel.*enrich/i.test(String(item.title || item.id || ''))) : null;
    const curve = (requested && requested.writeAllowed !== false ? requested : null) ||
      (preferred && preferred.writeAllowed !== false ? preferred : null) ||
      curves.find(item => item.writeAllowed !== false) || requested || preferred || curves[0] || null;
    hierarchyCurveId = curve ? String(curve.id || '') : '';
    return {system, category, curve};
  }

  function workspaceCurveById(id) {
    return (workspace?.curves || []).find(item => String(item.id || '') === String(id || '')) || null;
  }

  function ensureCurveSession(selectedCurve, relatedCurves = []) {
    if (!selectedCurve) return;
    const id = String(selectedCurve.id || '');
    curveLoadedIds = curveLoadedIds.filter(loadedId => !!workspaceCurveById(loadedId));
    if (curveMode === 'single') {
      curveLoadedIds = [id];
      curveActiveId = id;
      curveVisibility = new Set([id]);
      return;
    }
    if (!curveLoadedIds.length) {
      curveLoadedIds = [id];
      for (const item of relatedCurves) {
        const relatedId = String(item?.id || '');
        if (!relatedId || curveLoadedIds.includes(relatedId)) continue;
        curveLoadedIds.push(relatedId);
        if (curveLoadedIds.length >= 4) break;
      }
      curveVisibility = new Set(curveLoadedIds);
    } else if (!curveLoadedIds.includes(id)) {
      curveLoadedIds.unshift(id);
      curveVisibility.add(id);
    }
    if (!curveActiveId || !workspaceCurveById(curveActiveId)) curveActiveId = id;
    curveVisibility.add(curveActiveId);
  }

  function curveSurfaceCurrent(item) {
    if (!item || selectedSurface?.kind !== 'curve') return false;
    if (String(selectedSurface.id || selectedSurface.surfaceId || '') !== String(item.id || '')) return false;
    return currentArrayIdentity(arrayDetail) && currentArrayIdentity(arrayXDetail);
  }

  function activateCurve(id) {
    const item = workspaceCurveById(id);
    if (!item) return false;
    curveActiveId = String(item.id || '');
    hierarchyCurveId = curveActiveId;
    if (!curveLoadedIds.includes(curveActiveId)) curveLoadedIds.push(curveActiveId);
    curveVisibility.add(curveActiveId);
    return prepareArraySurface(item, true);
  }

  function curveSeriesForItem(item) {
    if (!item) return null;
    const isActive = String(item.id || '') === String(curveActiveId || '') && curveSurfaceCurrent(item);
    const detail = isActive ? arrayDetail : getSemanticArrayDetail(String(item.targetArray || item.name || ''));
    const xDetail = isActive ? arrayXDetail : getSemanticArrayDetail(String(item.xBins || ''));
    if (!currentArrayIdentity(detail) || !currentArrayIdentity(xDetail)) return null;
    if (!Array.isArray(detail.values) || !Array.isArray(xDetail.values) || detail.values.length !== xDetail.values.length) return null;
    const targetName = String(item.targetArray || item.name || '');
    const y = detail.values.map((value, index) => {
      const pending = arrayDraftFor(targetName, index);
      return Number(pending ? pending.effectiveValue : value);
    });
    return {
      item,
      id:String(item.id || ''),
      x:xDetail.values.map(Number),
      y,
      xUnit:String(xDetail.unit || ''),
      yUnit:String(detail.unit || item.unit || ''),
      detail,
      xDetail
    };
  }

  function curveLoadedSeries() {
    return curveLoadedIds
      .map(id => workspaceCurveById(id))
      .filter(Boolean)
      .map(curveSeriesForItem)
      .filter(Boolean);
  }

  function svgNode(name, attrs = {}) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', name);
    for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, String(value));
    return node;
  }

  function curvePalette(index) {
    return ['#48c8ff','#7ee787','#ffb86b','#d2a8ff','#ff7b72','#a5d6ff'][index % 6];
  }

  function selectCurvePoint(index) {
    if (!selectedArray || !arrayDetail || index < 0 || index >= Number(arrayDetail.values?.length || 0)) return;
    selectArrayCell(index, null);
    renderCurveGraph();
    refreshInlineCurveInspector();
  }

  function renderCurveGraph() {
    const host = document.getElementById('t4twCurvePlot');
    if (!host) return;
    const hostRect=host.getBoundingClientRect();
    const graphWidth=Math.max(280,Math.round(Number(hostRect.width)||1000));
    const graphHeight=Math.max(150,Math.round(Number(hostRect.height)||520));
    host.textContent = '';
    const series = curveLoadedSeries().filter(s => curveVisibility.has(s.id));
    const active = series.find(s => s.id === curveActiveId) || series[0] || null;
    const svg = svgNode('svg', {viewBox:'0 0 '+graphWidth+' '+graphHeight, preserveAspectRatio:'none', class:'t4tw-curve-svg'});
    host.appendChild(svg);

    if (!active) {
      const text = svgNode('text', {x:graphWidth/2,y:graphHeight/2,'text-anchor':'middle',fill:'#819aa7','font-size':Math.min(24,Math.max(12,graphHeight*.08))});
      text.textContent = 'No visible curve';
      svg.appendChild(text);
      return;
    }

    const compatible = series.filter(s => s.xUnit === active.xUnit && s.yUnit === active.yUnit);
    const allX = compatible.flatMap(s => s.x).filter(Number.isFinite);
    const allY = compatible.flatMap(s => s.y).filter(Number.isFinite);
    if (!allX.length || !allY.length) return;
    let minX = Math.min(...allX), maxX = Math.max(...allX);
    let minY = Math.min(...allY), maxY = Math.max(...allY);
    if (maxX === minX) { minX -= 1; maxX += 1; }
    if (maxY === minY) { minY -= 1; maxY += 1; }
    const padY = (maxY - minY) * 0.08;
    minY -= padY; maxY += padY;

    const compact=graphWidth<620||graphHeight<300;
    const labelSize=compact?11:15;
    const left=compact?48:62, right=compact?10:18, top=compact?12:18, bottom=compact?34:46;
    const plotW=Math.max(1,graphWidth-left-right), plotH=Math.max(1,graphHeight-top-bottom);
    const sx=x => left + ((x-minX)/(maxX-minX))*plotW;
    const sy=y => top + (1-((y-minY)/(maxY-minY)))*plotH;

    const background = svgNode('rect',{x:left,y:top,width:plotW,height:plotH,fill:'#071119',stroke:'#263b48','stroke-width':1});
    svg.appendChild(background);
    for (let i=0;i<=5;i++) {
      const gx=left+(plotW*i/5), gy=top+(plotH*i/5);
      svg.appendChild(svgNode('line',{x1:gx,y1:top,x2:gx,y2:top+plotH,stroke:'#182a35','stroke-width':1}));
      svg.appendChild(svgNode('line',{x1:left,y1:gy,x2:left+plotW,y2:gy,stroke:'#182a35','stroke-width':1}));
      const xLabel=svgNode('text',{x:gx,y:top+plotH+(compact?16:22),'text-anchor':'middle',fill:'#78909c','font-size':labelSize});
      xLabel.textContent=formatNumber(minX+(maxX-minX)*i/5, active.xDetail);
      svg.appendChild(xLabel);
      const yLabel=svgNode('text',{x:left-(compact?6:10),y:gy+4,'text-anchor':'end',fill:'#78909c','font-size':labelSize});
      yLabel.textContent=formatNumber(maxY-(maxY-minY)*i/5, active.detail);
      svg.appendChild(yLabel);
    }
    const xUnit=svgNode('text',{x:left+plotW/2,y:graphHeight-4,'text-anchor':'middle',fill:'#91a8b4','font-size':labelSize});
    xUnit.textContent=active.xUnit || 'X';
    svg.appendChild(xUnit);
    const yUnitX=compact?12:16;
    const yUnit=svgNode('text',{x:yUnitX,y:top+plotH/2,'text-anchor':'middle',fill:'#91a8b4','font-size':labelSize,transform:'rotate(-90 '+yUnitX+' '+(top+plotH/2)+')'});
    yUnit.textContent=active.yUnit || 'Y';
    svg.appendChild(yUnit);

    compatible.filter(s => s.id !== active.id).forEach(s => {
      const points=s.x.map((x,i)=>[sx(x),sy(s.y[i])]).filter(pair=>pair.every(Number.isFinite));
      if (!points.length) return;
      const paletteIndex=Math.max(1,curveLoadedIds.indexOf(s.id)+1);
      const path=svgNode('path',{d:points.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join(' '),fill:'none',stroke:curvePalette(paletteIndex),'stroke-width':3,opacity:.72,'vector-effect':'non-scaling-stroke'});
      svg.appendChild(path);
      points.forEach(([x,y])=>svg.appendChild(svgNode('circle',{cx:x,cy:y,r:4,fill:curvePalette(paletteIndex),opacity:.85})));
    });

    const activePoints=active.x.map((x,i)=>[sx(x),sy(active.y[i]),i]).filter(pair=>Number.isFinite(pair[0])&&Number.isFinite(pair[1]));
    if (activePoints.length) {
      const path=svgNode('path',{d:activePoints.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join(' '),fill:'none',stroke:curvePalette(0),'stroke-width':4,'vector-effect':'non-scaling-stroke'});
      svg.appendChild(path);
      activePoints.forEach(([x,y,index]) => {
        const selected=selectedArrayCell===index || selectedArrayCells.has(index);
        const circle=svgNode('circle',{cx:x,cy:y,r:selected?10:7,fill:selected?'#ffffff':curvePalette(0),stroke:'#061018','stroke-width':3,'data-curve-point':index});
        circle.style.cursor='pointer';
        circle.addEventListener('click',event=>{event.stopPropagation();selectCurvePoint(index);});
        svg.appendChild(circle);
      });
      const selectedPoint=activePoints.find(point=>point[2]===selectedArrayCell);
      if(selectedPoint){
        const [px,py,index]=selectedPoint;
        svg.appendChild(svgNode('line',{x1:px,y1:py,x2:px,y2:top+plotH,stroke:'#a9c9d8','stroke-width':1.5,'stroke-dasharray':'7 6',opacity:.9}));
        const boxWidth=Math.min(156,Math.max(118,graphWidth*.34));
        const tx=Math.min(graphWidth-right-boxWidth-2,Math.max(left+6,px+10));
        const ty=Math.max(top+48,py-50);
        const box=svgNode('rect',{x:tx,y:ty-34,width:boxWidth,height:54,rx:7,fill:'#081923',stroke:'#1cbcff','stroke-width':1.5});
        svg.appendChild(box);
        const line1=svgNode('text',{x:tx+8,y:ty-17,fill:'#24c7ff','font-size':labelSize,'font-weight':'700'});line1.textContent=String(active.item?.title||active.id||'Curve');
        const line2=svgNode('text',{x:tx+8,y:ty,fill:'#e5f4fa','font-size':labelSize});line2.textContent='X: '+valueWithUnit(active.x[index],active.xDetail);
        const line3=svgNode('text',{x:tx+8,y:ty+15,fill:'#e5f4fa','font-size':labelSize});line3.textContent='Y: '+valueWithUnit(active.y[index],active.detail);
        svg.append(line1,line2,line3);
      }
    }

    const mismatch=series.filter(s => s.id !== active.id && (s.xUnit !== active.xUnit || s.yUnit !== active.yUnit)).length;
    if (mismatch) {
      const note=svgNode('text',{x:graphWidth-right,y:top+labelSize,'text-anchor':'end',fill:'#d6b966','font-size':labelSize});
      note.textContent=mismatch+' visible curve(s) hidden: axis mismatch';
      svg.appendChild(note);
    }
  }

  function refreshInlineCurveInspector() {
    const selected=document.getElementById('t4twInlineCurveSelection');
    const xNode=document.getElementById('t4twInlineCurveX');
    const yInput=document.getElementById('t4twInlineCurveValue');
    const status=document.getElementById('t4twInlineCurveStatus');
    const range=document.getElementById('t4twInlineCurveRange');
    if (range) {
      range.textContent=arrayRangeSelectArmed?'Range ✓':'Range';
      range.classList.toggle('active',arrayRangeSelectArmed);
    }
    const count=selectedArrayCells.size || (selectedArrayCell>=0?1:0);
    if (selected) selected.textContent=count+' selected';
    if (selectedArrayCell>=0 && Array.isArray(arrayXDetail?.values) && Array.isArray(arrayDetail?.values)) {
      if (xNode) xNode.textContent='X '+valueWithUnit(arrayXDetail.values[selectedArrayCell],arrayXDetail);
      if (yInput) {
        const d=arrayDraftFor(selectedArray?.name,selectedArrayCell);
        yInput.disabled=selectedSurface?.writeAllowed===false;
        yInput.value=String(d?d.requestedValue:arrayDetail.values[selectedArrayCell]);
      }
      if (status) status.textContent='Active: '+String(selectedSurface?.title || selectedSurface?.id || curveActiveId)+
        (count>1?' • '+count+' points selected':'')+
        (arrayDraftFor(selectedArray?.name,selectedArrayCell)?' • pending edit':'');
    } else {
      if (xNode) xNode.textContent='X —';
      if (yInput) { yInput.value=''; yInput.disabled=true; }
      if (status) status.textContent='Select a point on the active curve.';
    }
  }

  function applyInlineCurveSelection(mode, operand) {
    if (selectedSurface?.writeAllowed === false) {
      const status=document.getElementById('t4twInlineCurveStatus');
      if(status)status.textContent=selectedSurface.writeBlockReason||'Current INI conditions make this curve read-only.';
      return;
    }
    if (!ensureInlineTableSelection()) {
      const status=document.getElementById('t4twInlineCurveStatus');
      if (status) status.textContent='Select one or more active-curve points first.';
      return;
    }
    if (!Number.isFinite(Number(operand))) return;
    regionOperation.value=mode;
    regionValue.value=String(operand);
    stageArraySelectionEdit();
  }

  function currentCurveDraftChanges() {
    if (!selectedArray?.name) return [];
    return Object.values(arrayDraft)
      .filter(entry=>entry && entry.arrayName===selectedArray.name && entry.tuneFingerprint===workspace?.tuneFingerprint)
      .map(entry=>currentArrayWrite(entry.arrayName,entry.cellIndex))
      .filter(Boolean);
  }

  function writeCurrentCurveDrafts() {
    const changes=currentCurveDraftChanges();
    const status=document.getElementById('t4twInlineCurveStatus');
    if (!changes.length) {
      if (status) status.textContent='No pending edits for the active curve.';
      return;
    }
    if (queueSemanticWrites(changes,status) && status) {
      status.textContent=changes.length+' curve point edit(s) queued for verified ECU RAM write.';
    }
  }

  function renderCurveManager(container, allCurves) {
    const head=document.createElement('div');
    head.className='t4tw-curve-manager-head';
    head.textContent=curveMode==='multi'?'Loaded curves':'Active curve';
    const body=document.createElement('div');
    body.className='t4tw-curve-list';

    for (const id of curveLoadedIds) {
      const item=workspaceCurveById(id);
      if (!item) continue;
      const row=document.createElement('div');
      row.className='t4tw-curve-row'+(id===curveActiveId?' active':'');
      row.tabIndex=0;
      row.setAttribute('role','button');
      row.setAttribute('aria-label','Make '+String(item.title||item.id)+' the active editable curve');
      const paletteIndex=id===curveActiveId?0:Math.max(1,curveLoadedIds.indexOf(id)+1);
      row.style.borderLeftColor=curvePalette(paletteIndex);
      row.style.borderLeftWidth='3px';
      const visible=document.createElement('input');
      visible.type='checkbox';
      visible.checked=curveVisibility.has(id);
      visible.disabled=id===curveActiveId;
      visible.setAttribute('aria-label','Toggle curve visibility');
      visible.onclick=event=>event.stopPropagation();
      visible.onchange=()=>{
        if (visible.checked) curveVisibility.add(id); else curveVisibility.delete(id);
        if (id===curveActiveId) curveVisibility.add(id);
        renderCurveGraph();
        render();
      };
      const name=document.createElement('div');
      name.className='t4tw-curve-row-name';
      name.textContent=item.title||item.id;
      const meta=document.createElement('div');
      meta.className='t4tw-curve-row-meta';
      const series=curveSeriesForItem(item);
      meta.textContent=(id===curveActiveId?'ACTIVE • ':'')+
        (series?((series.xUnit||'X')+' / '+(series.yUnit||'Y')):'unavailable');
      name.appendChild(meta);
      const actions=document.createElement('div');
      actions.className='t4tw-curve-row-actions';
      if(id===curveActiveId){
        const activeBadge=document.createElement('span');
        activeBadge.className='t4tw-curve-mini active';
        activeBadge.textContent='Active';
        actions.appendChild(activeBadge);
      } else if(curveMode==='multi' && curveLoadedIds.length>1) {
        const remove=document.createElement('button');
        remove.type='button';
        remove.className='t4tw-curve-mini';
        remove.textContent='×';
        remove.setAttribute('aria-label','Remove '+String(item.title||item.id)+' from Multi view');
        remove.onclick=event=>{
          event.stopPropagation();
          curveLoadedIds=curveLoadedIds.filter(value=>value!==id);
          curveVisibility.delete(id);
          render();
        };
        actions.appendChild(remove);
      }
      const activate=()=>{
        if(id===curveActiveId)return;
        if(activateCurve(id)){curveVisibility.add(id);render();}
      };
      row.onclick=event=>{if(event.target===visible||event.target.closest('button'))return;activate();};
      row.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();activate();}};
      row.append(visible,name,actions);
      body.appendChild(row);
    }
    container.append(head,body);

    if (curveMode==='multi') {
      const add=document.createElement('details');
      add.className='t4tw-curve-add t4tw-menu';
      const summary=document.createElement('summary');
      summary.className='t4tw-btn';
      summary.textContent='+  Add Curve';
      const panel=document.createElement('div');
      panel.className='t4tw-menu-panel';
      const select=document.createElement('select');
      const candidates=allCurves.filter(item=>!curveLoadedIds.includes(String(item.id||'')));
      const placeholder=document.createElement('option');
      placeholder.value='';
      placeholder.textContent=candidates.length?'Choose curve…':'All curves loaded';
      select.appendChild(placeholder);
      candidates.forEach(item=>{
        const option=document.createElement('option');
        option.value=String(item.id||'');
        option.textContent=item.title||item.id;
        select.appendChild(option);
      });
      const button=document.createElement('button');
      button.type='button';
      button.className='t4tw-btn';
      button.textContent='Add selected curve';
      button.disabled=!candidates.length;
      button.onclick=()=>{
        const id=String(select.value||'');
        if(!id)return;
        if(!curveLoadedIds.includes(id))curveLoadedIds.push(id);
        curveVisibility.add(id);
        add.open=false;
        render();
      };
      panel.append(select,button);
      add.append(summary,panel);
      container.appendChild(add);
    }
  }

  function renderCurvesMode(q) {
    const systems=surfaceHierarchy('curve',q);
    const selected=ensureCurveHierarchySelection(systems);
    const fragment=document.createDocumentFragment();
    if (!systems.length || !selected.curve) {
      const empty=document.createElement('div');
      empty.className='t4tw-empty';
      empty.textContent=q?'No current-INI curves match this search.':'No current-INI curves are available.';
      fragment.appendChild(empty);
      list.textContent='';
      list.appendChild(fragment);
      summary.textContent='0 Curves';
      updateChangeCount();
      return;
    }

    ensureCurveSession(selected.curve, selected.category?.items || []);
    if (!curveActiveId) curveActiveId=String(selected.curve.id||'');
    const activeItem=workspaceCurveById(curveActiveId) || selected.curve;
    if (!curveSurfaceCurrent(activeItem) && !activateCurve(String(activeItem.id||''))) {
      const empty=document.createElement('div');
      empty.className='t4tw-empty';
      empty.textContent=meta.textContent||'Active curve could not be loaded.';
      list.textContent='';
      list.appendChild(empty);
      return;
    }

    const shell=document.createElement('div');
    shell.className='t4tw-curve-shell';
    shell.dataset.uiContract='responsive-curves-v1';
    const rail=renderSystemRail(systems,selected.system?.name||'',system=>{
      hierarchySystem=system.name;
      hierarchyCategory=system.categories[0]?.name||'';
      hierarchyCurveId='';
      curveActiveId='';
      render();
    });
    const mainPane=document.createElement('div');
    mainPane.className='t4tw-curve-main';

    const selectors=document.createElement('div');
    selectors.className='t4tw-hierarchy-selectors';
    selectors.dataset.uiContract='responsive-hierarchy-v1';
    const systemSelect=hierarchySelect('System',selected.system?.name||'',
      systems.map(system=>({value:system.name,label:system.name})),value=>{
        hierarchySystem=value;
        const next=systems.find(system=>system.name===value);
        hierarchyCategory=next?.categories?.[0]?.name||'';
        hierarchyCurveId='';
        curveActiveId='';
        render();
      });
    const categories=selected.system?.categories||[];
    const categorySelect=hierarchySelect('Category',selected.category?.name||'',
      categories.map(category=>({value:category.name,label:category.name})),value=>{
        hierarchyCategory=value;
        hierarchyCurveId='';
        curveActiveId='';
        render();
      });
    const curves=selected.category?.items||[];
    const curveSelect=hierarchySelect('Curve',selected.curve?.id||'',
      curves.map(item=>({value:String(item.id||''),label:item.title||item.id,disabled:item.writeAllowed===false})),value=>{
        hierarchyCurveId=String(value||'');
        const item=workspaceCurveById(hierarchyCurveId);
        if (item) {
          if (curveMode==='single') {
            curveLoadedIds=[hierarchyCurveId];
            curveVisibility=new Set([hierarchyCurveId]);
          } else if (!curveLoadedIds.includes(hierarchyCurveId)) {
            curveLoadedIds.push(hierarchyCurveId);
            curveVisibility.add(hierarchyCurveId);
          }
          activateCurve(hierarchyCurveId);
        }
        render();
      });
    selectors.append(systemSelect,hierarchyChevron('system'),categorySelect,hierarchyChevron('category'),curveSelect);
    appendResponsiveNav(mainPane, selectors);

    const head=document.createElement('div');
    head.className='t4tw-curve-head';
    const titleBlock=document.createElement('div');
    titleBlock.className='t4tw-curve-title-block';
    const title=document.createElement('div');
    title.className='t4tw-curve-title';
    title.textContent='Curves';
    const sub=document.createElement('div');
    sub.className='t4tw-curve-sub';
    const activeSeries=curveSeriesForItem(activeItem);
    sub.textContent=[
      String(activeItem.pointCount||'')+' points',
      activeSeries?.xUnit ? ('X '+activeSeries.xUnit) : '',
      activeSeries?.yUnit ? ('Y '+activeSeries.yUnit) : '',
      curveMode==='multi' ? (curveLoadedIds.length+' loaded') : ''
    ].filter(Boolean).join(' • ');
    titleBlock.append(title,sub);
    const mode=document.createElement('div');
    mode.className='t4tw-curve-mode';
    for (const name of ['single','multi']) {
      const button=document.createElement('button');
      button.type='button';
      button.className='t4tw-btn'+(curveMode===name?' active':'');
      button.textContent=name==='single'?'Single':'Multi';
      button.onclick=()=>{
        if (curveMode===name) return;
        curveMode=name;
        if (name==='single') {
          curveLoadedIds=[curveActiveId];
          curveVisibility=new Set([curveActiveId]);
        } else {
          if (!curveLoadedIds.includes(curveActiveId)) curveLoadedIds.push(curveActiveId);
          curveVisibility.add(curveActiveId);
        }
        render();
      };
      mode.appendChild(button);
    }
    head.append(titleBlock,mode);
    mainPane.appendChild(head);

    const workspaceNode=document.createElement('div');
    workspaceNode.className='t4tw-curve-workspace';
    const plot=document.createElement('div');
    plot.className='t4tw-curve-plot-wrap';
    plot.id='t4twCurvePlot';
    const manager=document.createElement('aside');
    manager.className='t4tw-curve-manager';
    renderCurveManager(manager,(workspace?.curves||[]));
    workspaceNode.append(plot,manager);
    mainPane.appendChild(workspaceNode);

    const editbar=document.createElement('div');
    editbar.className='t4tw-curve-editbar';
    const selection=document.createElement('span');
    selection.id='t4twInlineCurveSelection';
    selection.className='t4tw-table-selection';
    selection.textContent=(selectedArrayCells.size||(selectedArrayCell>=0?1:0))+' selected';
    const activeLabel=document.createElement('span');
    activeLabel.className='t4tw-table-selection';
    activeLabel.append(document.createTextNode('Active: '));
    const activeName=document.createElement('b');
    activeName.style.color='#23c5f6';
    activeName.textContent=String(activeItem.title||activeItem.id||'Curve');
    activeLabel.appendChild(activeName);
    const xNode=document.createElement('span');
    xNode.id='t4twInlineCurveX';
    xNode.className='t4tw-table-selection';
    xNode.textContent='X —';
    const delta=value=>{
      const button=document.createElement('button');
      button.type='button';
      button.className='t4tw-btn';
      button.textContent=value>0?('+'+value):String(value);
      button.disabled=activeItem.writeAllowed===false;
      button.onclick=()=>applyInlineCurveSelection(value>=0?'add':'subtract',Math.abs(value));
      return button;
    };
    const input=document.createElement('input');
    input.id='t4twInlineCurveValue';
    input.className='t4tw-curve-value-input';
    input.inputMode='decimal';
    input.placeholder='Y value';
    input.disabled=activeItem.writeAllowed===false;
    const set=document.createElement('button');
    set.type='button';
    set.className='t4tw-btn active';
    set.textContent='Set';
    set.disabled=activeItem.writeAllowed===false;
    set.onclick=()=>applyInlineCurveSelection('set',Number(input.value));
    input.addEventListener('keydown',event=>{if(event.key==='Enter')applyInlineCurveSelection('set',Number(input.value));});
    const undo=document.createElement('button');undo.type='button';undo.className='t4tw-btn';undo.textContent='↶ Undo';undo.disabled=true;
    const redo=document.createElement('button');redo.type='button';redo.className='t4tw-btn';redo.textContent='↷ Redo';redo.disabled=true;
    const selectMenu=inlineMenu('Select ',[
      {label:'Point range',action:()=>{if(selectedArrayCell>=0){arrayRangeSelectArmed=!arrayRangeSelectArmed;if(arrayRangeSelectArmed)selectedArrayAnchor=selectedArrayCell;refreshInlineCurveInspector();}}},
      {label:'All points',action:()=>{selectAllArrayCells();renderCurveGraph();refreshInlineCurveInspector();}},
      {label:'Clear',action:()=>{arrayRangeSelectArmed=false;clearArraySelection();refreshInlineCurveInspector();renderCurveGraph();}}
    ]);
    const toolsMenu=inlineMenu('Tools ',[
      {label:'Interpolate selected endpoints',disabled:true},
      {label:'Smooth',disabled:true},
      {label:'Copy',disabled:true},
      {label:'Paste',disabled:true}
    ]);
    editbar.append(selection,activeLabel,xNode,delta(-1),delta(-0.1),delta(0.1),delta(1),input,set,undo,redo,selectMenu,toolsMenu);
    mainPane.appendChild(editbar);
    const status=document.createElement('div');
    status.id='t4twInlineCurveStatus';
    status.className='t4tw-curve-status';
    mainPane.appendChild(status);

    shell.append(rail,mainPane);
    fragment.appendChild(shell);
    list.textContent='';
    list.appendChild(fragment);
    renderCurveGraph();
    refreshInlineCurveInspector();

    const pendingCount=Object.values(arrayDraft).filter(entry=>entry&&entry.tuneFingerprint===workspace?.tuneFingerprint).length;
    summary.textContent=Number(workspace.readableCurves||0)+' readable curve(s) • '+
      curveLoadedIds.length+' loaded • '+pendingCount+' pending table/curve cell edit(s) • INI '+
      String(workspace.iniCompatibility?.state||'amber').toUpperCase();
    updateChangeCount();
  }

  function renderGroupedSurfaces(q, kindFilter = '') {
    const allSurfaces = [].concat(Array.isArray(workspace.tables) ? workspace.tables : [], Array.isArray(workspace.curves) ? workspace.curves : [])
      .filter(item => !kindFilter || String(item.kind || '') === kindFilter);
    const grouping = buildIniGroupedRoutes('surface');
    const routes = grouping.routes
      .map(route => Object.assign({}, route, {items: route.items.filter(item => (!kindFilter || String(item.kind || '') === kindFilter) && surfaceMatchesView(item, q, route))}))
      .filter(route => route.items.length);
    const ungrouped = allSurfaces
      .filter(item => !grouping.covered.has(String(item.id)))
      .filter(item => surfaceMatchesView(item, q));
    const fragment = document.createDocumentFragment();
    const state = {menu:null, group:null};
    let shown = 0;
    const MAX_ROWS = 250;
    const distinct = new Set();
    for (const route of routes) {
      if (shown >= MAX_ROWS) break;
      const available = route.items.slice(0, MAX_ROWS - shown);
      if (!available.length) continue;
      appendIniRouteHeader(route, state, fragment);
      for (const item of available) {
        appendSurfaceRow(item, fragment);
        distinct.add(String(item.id));
        shown++;
      }
    }
    if (shown < MAX_ROWS && ungrouped.length) {
      appendUngroupedHeader('Readable tables / curves', fragment);
      for (const item of ungrouped.slice(0, MAX_ROWS - shown)) {
        appendSurfaceRow(item, fragment);
        distinct.add(String(item.id));
        shown++;
      }
    }
    if (!shown) {
      const empty = document.createElement('div');
      empty.className = 't4tw-empty';
      empty.textContent = activeFilter === 'pending'
        ? 'No pending table/curve edits match this INI grouping/search.'
        : activeFilter === 'favorites'
          ? 'No favorite tables or curves match this INI grouping/search.'
          : 'No readable current-INI tables or curves match this search.';
      fragment.appendChild(empty);
    }
    list.textContent = '';
    list.appendChild(fragment);
    const placements = routes.reduce((sum, route) => sum + route.items.length, 0) + ungrouped.length;
    const suffix = placements > shown ? ' • showing first ' + shown : '';
    const pendingCount = Object.values(arrayDraft).filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint).length;
    const readableSurfaces = Number(workspace.readableTables || 0) + Number(workspace.readableCurves || 0);
    const totalSurfaces = Number(workspace.totalProfileTables || 0) + Number(workspace.totalProfileCurves || 0);
    const skippedSurfaces = Number(workspace.skippedTables || 0) + Number(workspace.skippedCurves || 0);
    const ambiguousSurfaces = Number(workspace.ambiguousTableIds || 0) + Number(workspace.ambiguousCurveIds || 0);
    summary.textContent = distinct.size + ' unique matching tables/curves • ' + placements + ' INI placements • ' +
      routes.length + ' menu/dialog routes • ' + ungrouped.length + ' ungrouped • ' +
      readableSurfaces + ' readable / ' + totalSurfaces + ' INI editors • ' +
      skippedSurfaces + ' skipped • ' + ambiguousSurfaces + ' ambiguous • ' +
      pendingCount + ' pending cells' + suffix;
    updateChangeCount();
  }

  function render() {
    if (!workspace || workspace.status !== 'ready') return;
    list.classList.toggle('t4tw-fullarea', viewMode === 'tables' || viewMode === 'curves');
    const q = (search.value || '').trim().toLowerCase();
    if (viewMode === 'settings') {
      renderSettingsMode(q);
      return;
    }
    if (viewMode === 'tables') {
      renderTablesMode(q);
      return;
    }
    if (viewMode === 'curves') {
      renderCurvesMode(q);
      return;
    }
    renderGroupedScalars(q);
  }

  function nativeWriteStateSignature() {
    return JSON.stringify({
      tunerStatus: String(tuningWriteStatus?.status || ''),
      tunerDirty: Number(tuningWriteStatus?.dirtyPageCount || 0),
      tunerUncertain: tuningWriteStatus?.uncertain === true
    });
  }

  function readNativeWriteStatus() {
    try {
      const rawTuning = window.EpicDashAndroid?.getTuningWriteStatusJson?.();
      tuningWriteStatus = rawTuning ? JSON.parse(rawTuning) : null;
    } catch (_) {
      tuningWriteStatus = null;
    }
    updateNormalTuningControls();
  }

  function refreshNativeWriteStatus(refreshWorkspaceOnChange = true) {
    const before = lastNativeWriteStateSignature;
    readNativeWriteStatus();
    const after = nativeWriteStateSignature();
    lastNativeWriteStateSignature = after;
    if (refreshWorkspaceOnChange && before && after !== before && page.classList.contains('active')) {
      loadWorkspace();
    }
  }

  function iniImportPhaseLabel(phase) {
    return ({selecting:'Select file…',reading:'Reading INI…',parsing:'Parsing INI…',applying:'Applying INI…',restoring:'Restoring INI…'})[phase] || 'Import INI';
  }

  function applyIniImportState(state) {
    iniImportState = Object.assign({}, iniImportState, state || {});
    const busy = iniImportState.busy === true;
    if (importIniButton) {
      importIniButton.disabled = busy;
      importIniButton.textContent = busy ? iniImportPhaseLabel(iniImportState.phase) : 'Import INI';
    }
    if (busy || ['complete','failed','cancelled'].includes(String(iniImportState.phase || ''))) {
      const timing = Number(iniImportState.totalElapsedMs || 0) > 0 ? (' • total ' + Number(iniImportState.totalElapsedMs) + ' ms') : '';
      meta.textContent = String(iniImportState.message || iniImportPhaseLabel(iniImportState.phase)) + timing;
      if (['complete','failed','cancelled'].includes(String(iniImportState.phase || ''))) {
        setTimeout(() => { try { const raw=window.EpicDashAndroid?.getIniCompatibilityJson?.(); if(raw)window.EpicDashApplyIniCompatibility?.(JSON.parse(raw)); } catch(_) {} }, 0);
      }
    }
  }

  function readIniImportState() {
    try {
      const raw = window.EpicDashAndroid?.getUsbIniImportStatusJson?.();
      applyIniImportState(raw ? JSON.parse(raw) : null);
    } catch (_) {}
  }

  function importIni() {
    try {
      const bridge = window.EpicDashAndroid?.importUsbIni;
      if (typeof bridge !== 'function') throw new Error('Native INI import bridge unavailable');
      bridge.call(window.EpicDashAndroid);
      readIniImportState();
    } catch (error) {
      meta.textContent = String(error && error.message || error);
    }
  }

  const TUNER_TELEMETRY_KEYS = ['rpm','map','tps','afr','clt','batt','iat','fuelPressure','oilPressure','ign','boostDuty'];
  function syncTunerChrome() {
    const iniState = String(document.getElementById('iniStateLabel')?.textContent || workspace?.iniCompatibility?.state || 'amber').toLowerCase();
    if (topIniButton) {
      topIniButton.classList.remove('red','amber','green');
      topIniButton.classList.add(['red','amber','green'].includes(iniState) ? iniState : 'amber');
      if (topIniLabel) topIniLabel.textContent = 'INI';
      topIniButton.title = document.getElementById('iniStatePill')?.title || 'INI status';
    }
    const dirtyPages = Number(tuningWriteStatus?.dirtyPageCount || workspace?.dirtyPageCount || 0);
    const semanticDirty = burnDirtySemanticKeys.size;
    if (topBurnButton) {
      if (topBurnLabel) topBurnLabel.textContent = semanticDirty > 0 ? ('Burn ' + semanticDirty) : 'Burn';
      topBurnButton.classList.toggle('clean', semanticDirty <= 0 && dirtyPages <= 0);
      topBurnButton.disabled = tuningWriteStatus?.operationRunning === true || tuningWriteStatus?.uncertain === true || pendingChangeCount() > 0;
      topBurnButton.title = semanticDirty > 0
        ? (semanticDirty + ' verified unburned semantic item' + (semanticDirty===1?'':'s'))
        : (dirtyPages > 0 ? 'ECU reports unburned pages; semantic item count is unavailable for changes not made in this Tuner session.' : 'No verified unburned Tuner edits.');
    }
    let live = false;
    try { live = window.EpicDashTunerTelemetrySnapshot?.()?.connected === true; } catch (_) {}
    if (topEcuState) topEcuState.classList.toggle('connected', live || workspace?.status === 'ready');
    const main = topEcuState?.querySelector('b');
    if (main) main.textContent = (live || workspace?.status === 'ready') ? 'ECU Connected' : 'ECU Offline';
    const profileLabel = workspace?.importedProfileName || workspace?.profileName || 'mainController.ini';
    if (topProfileLabel) topProfileLabel.textContent = profileLabel;
    if (topEcuSub) topEcuSub.textContent = workspace?.ecuSignature || profileLabel;
  }

  function formatTelemetryValue(value, key) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return '—';
    if (['tps','afr','batt','ign'].includes(key)) return numeric.toFixed(1);
    if (key === 'boostDuty') return numeric.toFixed(0);
    return Math.round(numeric).toString();
  }

  function renderTunerTelemetry() {
    if (!telemetryStrip || !page.classList.contains('active')) return;
    let snapshot = null;
    try { snapshot = window.EpicDashTunerTelemetrySnapshot?.() || null; } catch (_) {}
    if (!snapshot) return;
    const labels = {rpm:'RPM',map:'MAP',tps:'TPS',afr:'AFR',clt:'CLT',iat:'IAT',fuelPressure:'Fuel P',oilPressure:'Oil P',ign:'Ign Adv',boostDuty:'WG DC',batt:'Batt'};
    telemetryStrip.textContent = '';
    for (const key of TUNER_TELEMETRY_KEYS) {
      const slot = document.createElement('div');
      slot.className = 't4tw-telemetry-slot';
      const label = document.createElement('div');
      label.className = 't4tw-telemetry-label';
      label.textContent = labels[key] || snapshot.meta?.[key]?.label || key;
      const value = document.createElement('div');
      value.className = 't4tw-telemetry-value';
      const number = document.createElement('b');
      number.textContent = snapshot.valid?.[key] === false ? '—' : formatTelemetryValue(snapshot.values?.[key], key);
      const unit = document.createElement('small');
      unit.textContent = snapshot.meta?.[key]?.unit || '';
      value.append(number, unit);
      slot.append(label, value);
      telemetryStrip.appendChild(slot);
    }
    syncTunerChrome();
  }

  function updateNormalTuningControls() {
    const busy = tuningWriteStatus?.operationRunning === true;
    const uncertain = tuningWriteStatus?.uncertain === true;
    const dirty = Number(tuningWriteStatus?.dirtyPageCount || workspace?.dirtyPageCount || 0);
    const pending = pendingChangeCount();
    if (readEcuButton) {
      readEcuButton.disabled = busy;
      readEcuButton.textContent = uncertain ? 'Read ECU • REQUIRED' : 'Read ECU';
    }
    if (saveEcuButton) {
      saveEcuButton.disabled = busy || uncertain || pending > 0;
      saveEcuButton.textContent = pending > 0
        ? ('Save / Burn ECU • write ' + pending + ' pending first')
        : (dirty > 0 ? ('Save / Burn ECU • ' + dirty + ' page' + (dirty === 1 ? '' : 's')) : 'Save / Burn ECU');
    }
    if (writeAllButton) {
      writeAllButton.disabled = busy || uncertain || pending === 0;
      writeAllButton.textContent = pending > 0
        ? ('WRITE ALL ' + pending + ' PENDING EDIT' + (pending === 1 ? '' : 'S') + ' TO ECU RAM')
        : 'NO PENDING EDITS TO WRITE';
    }
    if (saveChangesButton) {
      saveChangesButton.disabled = busy || uncertain || pending > 0;
      saveChangesButton.textContent = pending > 0 ? 'Save / Burn ECU • write pending edits first' : 'Save / Burn ECU';
    }
    updateArraySelectionControls();
    syncTunerChrome();
  }

  function readTuningFromEcu() {
    try {
      const bridge = window.EpicDashAndroid?.readTuningFromEcuJson;
      if (typeof bridge !== 'function') throw new Error('Native Read ECU bridge unavailable');
      const raw = bridge.call(window.EpicDashAndroid);
      const result = raw ? JSON.parse(raw) : {status:'error', reason:'No response from Read ECU bridge'};
      if (result.status !== 'queued') throw new Error(result.reason || 'Read ECU was not queued');
      meta.textContent = 'Reading complete tune from ECU…';
    } catch (error) {
      meta.textContent = String(error && error.message || error);
    }
  }

  function saveTuningToEcu() {
    const pending = pendingChangeCount();
    if (pending > 0) {
      meta.textContent = 'Write all pending edits to ECU RAM before Save / Burn ECU.';
      return;
    }
    try {
      const bridge = window.EpicDashAndroid?.burnTuningChangesJson;
      if (typeof bridge !== 'function') throw new Error('Native Save / Burn ECU bridge unavailable');
      const raw = bridge.call(window.EpicDashAndroid);
      const result = raw ? JSON.parse(raw) : {status:'error', reason:'No response from Save / Burn ECU bridge'};
      if (result.status !== 'queued') throw new Error(result.reason || 'Save / Burn ECU was not queued');
      meta.textContent = 'Saving current RAM tune to ECU flash…';
      changesModal.hidden = true;
    } catch (error) {
      meta.textContent = String(error && error.message || error);
    }
  }

  function allDraftChanges() {
    const scalarChanges = Object.keys(draft)
      .map(name => currentScalarWrite(name))
      .filter(Boolean);
    const bitChanges = Object.keys(bitDraft)
      .map(name => currentBitWrite(name))
      .filter(Boolean);
    const arrayChanges = Object.values(arrayDraft)
      .filter(entry => entry && entry.tuneFingerprint === workspace?.tuneFingerprint)
      .map(entry => currentArrayWrite(entry.arrayName, entry.cellIndex))
      .filter(Boolean);
    return scalarChanges.concat(bitChanges, arrayChanges);
  }

  function writeAllDrafts() {
    const changes = allDraftChanges();
    if (!changes.length) {
      meta.textContent = 'No pending edits to write.';
      if (changesStatus) {
        changesStatus.className = 't4tw-status bad';
        changesStatus.textContent = 'No pending edits are bound to the current ECU tune fingerprint.';
      }
      return;
    }
    if (changesStatus) {
      changesStatus.className = 't4tw-status';
      changesStatus.textContent = 'Submitting ' + changes.length + ' semantic edit(s) to native ECU write validation…';
    }
    if (queueSemanticWrites(changes, null)) {
      meta.textContent = changes.length + ' edit(s) queued for ECU RAM write and full-tune verification.';
      changesModal.hidden = true;
    }
  }

  function writeSelectedArrayCell() {
    if (!selectedArray || selectedArrayCell < 0) return;
    const change = currentArrayWrite(selectedArray.name, selectedArrayCell);
    if (!change) {
      arrayStatus.className = 't4tw-status bad';
      arrayStatus.textContent = 'Apply the edit first so this cell is staged as a pending change.';
      return;
    }
    queueSemanticWrites([change], arrayStatus);
  }

  function loadWorkspace() {
    let data;
    try {
      const raw = window.EpicDashAndroid?.getTuningWorkspaceJson?.();
      data = raw ? JSON.parse(raw) : {status:'not_ready', reason:'Native tuning bridge unavailable'};
    } catch (error) {
      data = {status:'error', reason:String(error && error.message || error)};
    }
    readNativeWriteStatus();
    lastNativeWriteStateSignature = nativeWriteStateSignature();
    workspace = data;
    const currentProfileFingerprint=String(data?.profileFingerprint||'');
    if(burnBaselineProfileFingerprint && currentProfileFingerprint && burnBaselineProfileFingerprint!==currentProfileFingerprint) {
      resetSemanticBurnAuthority(currentProfileFingerprint);
    } else if(!burnBaselineProfileFingerprint && currentProfileFingerprint) {
      burnBaselineProfileFingerprint=currentProfileFingerprint;
    }
    window.EpicDashApplyIniCompatibility?.(data.iniCompatibility || {state:'amber', reason:data.reason || 'INI compatibility is not current'});
    rebuildWorkspaceIndex();
    if (data.status !== 'ready') {
      meta.textContent = data.reason || 'Current tuning snapshot is not ready.';
      summary.textContent = 'Read-only workspace unavailable.';
      list.innerHTML = '<div class="t4tw-empty">USB must be streaming with a complete current-generation TuneSnapshot.</div>';
      draft = {};
      arrayDraft = {};
      bitDraft = {};
      resetSemanticBurnAuthority('');
      updateChangeCount();
      syncTunerChrome();
      return;
    }
    loadDraft();
    loadArrayDraft();
    loadBitDraft();
    const age = Number.isFinite(data.capturedAtEpochMs) ? Math.max(0, Math.round((Date.now() - data.capturedAtEpochMs) / 1000)) : null;
    const hash = String(data.tuneFingerprint || '').slice(0, 12);
    const dirty = Number(tuningWriteStatus?.dirtyPageCount || data.dirtyPageCount || 0);
    const writeState = String(tuningWriteStatus?.status || data.writeStatus || 'idle');
    if (iniImportState.busy !== true) {
      meta.textContent = `${data.importedProfileName || 'mainController.ini'} • gen ${data.generation} • tune ${hash || '—'}${age === null ? '' : ` • snapshot ${age}s ago`} • ${dirty} dirty page(s) • ${writeState}`;
    } else {
      applyIniImportState(iniImportState);
    }
    updateNormalTuningControls();
    render();
    // The native write callback may replace the TuneSnapshot while a Parameters
    // dialog remains open. Rebuild that dialog too so its controls and event closures
    // use the newly verified ECU baseline rather than the pre-write bit/scalar objects.
    rerenderOpenSettingsDialog();
  }

  function loadWorkspaceForActivation() {
    readNativeWriteStatus();
    const generation = Number(tuningWriteStatus?.generation || -1);
    const fingerprint = String(tuningWriteStatus?.currentTuneFingerprint || '');
    const alreadyCurrent = workspace?.status === 'ready' &&
      generation > 0 &&
      Number(workspace.generation) === generation &&
      fingerprint &&
      String(workspace.tuneFingerprint || '') === fingerprint &&
      tuningWriteStatus?.uncertain !== true;
    if (alreadyCurrent) {
      lastNativeWriteStateSignature = nativeWriteStateSignature();
      updateNormalTuningControls();
      return;
    }
    loadWorkspace();
  }
  search.addEventListener('input', render);
  modes.addEventListener('click', event => {
    const button = event.target.closest('[data-mode]');
    if (button) setViewMode(button.dataset.mode || 'settings');
  });
  filters.addEventListener('click', event => {
    const button = event.target.closest('[data-filter]');
    if (!button) return;
    activeFilter = button.dataset.filter || 'all';
    filters.querySelectorAll('[data-filter]').forEach(node => node.classList.toggle('active', node === button));
    render();
  });
  page.querySelectorAll('[data-shell-target]').forEach(button => {
    button.onclick = () => document.querySelector('.shellTab[data-shell="' + button.dataset.shellTarget + '"]')?.click();
  });
  if (topIniButton) topIniButton.onclick = () => document.getElementById('iniStatePill')?.click();
  if (topProfileButton) topProfileButton.onclick = () => document.getElementById('iniStatePill')?.click();
  if (topBurnButton) topBurnButton.onclick = saveTuningToEcu;
  if (topGearButton) topGearButton.onclick = () => document.getElementById('labSettingsBtn')?.click();
  importIniButton.onclick = importIni;
  refresh.onclick = loadWorkspace;
  readEcuButton.onclick = readTuningFromEcu;
  saveEcuButton.onclick = saveTuningToEcu;
  window.addEventListener('epicdash:page-activated', event => {
    const active = event?.detail?.pageId === 'page-tuning';
    document.body.classList.toggle('t4tw-tuner-active', active);
    if (active) {
      setTimeout(loadWorkspaceForActivation, 0);
      setTimeout(renderTunerTelemetry, 0);
    }
  });
  setInterval(() => { if (page.classList.contains('active')) renderTunerTelemetry(); }, 300);
  window.EpicDashTuningWriteStateChanged = () => {
    if (page.classList.contains('active')) setTimeout(settleQueuedSemanticWriteAndReload, 0);
  };
  window.addEventListener('epicdash-usb-ini-import-state', event => {
    applyIniImportState(event?.detail || null);
    if (event?.detail?.phase === 'complete' && page.classList.contains('active')) setTimeout(loadWorkspace, 0);
  });
  readIniImportState();
  document.addEventListener('visibilitychange', () => { if (!document.hidden && page.classList.contains('active')) setTimeout(loadWorkspaceForActivation, 0); });
  settingsClose.onclick = () => { settingsDialog.hidden = true; currentSettingsItem = null; };
  settingsDialog.onclick = event => { if (event.target === settingsDialog) { settingsDialog.hidden = true; currentSettingsItem = null; } };
  editorClose.onclick = closeEditor;
  editor.onclick = event => { if (event.target === editor) closeEditor(); };
  applyEditButton.onclick = stageSelectedEdit;
  applyRamButton.onclick = writeSelectedScalar;
  settingsWriteRam.onclick = writeAllDrafts;
  requestedInput.addEventListener('keydown', event => { if (event.key === 'Enter') stageSelectedEdit(); });
  changesButton.onclick = () => {
    renderChanges();
    if (changesStatus) {
      changesStatus.className = 't4tw-status';
      changesStatus.textContent = tuningWriteStatus?.operationRunning === true
        ? 'A tuning operation is still running.'
        : 'Ready to write ' + pendingChangeCount() + ' pending edit(s).';
    }
    changesModal.hidden = false;
  };
  changesClose.onclick = () => { changesModal.hidden = true; };
  changesModal.onclick = event => { if (event.target === changesModal) changesModal.hidden = true; };
  arrayClose.onclick = closeArrayEditor;
  arrayEditor.onclick = event => { if (event.target === arrayEditor) closeArrayEditor(); };
  arrayApplyEdit.onclick = stageArrayCellEdit;
  arrayWrite.onclick = writeSelectedArrayCell;
  selectRowButton.onclick = () => {
    if (selectedSurface?.kind !== 'table' || selectedArrayCell < 0) return;
    selectArrayRow(Math.floor(selectedArrayCell / Number(selectedSurface.xCount)));
  };
  selectColumnButton.onclick = () => {
    if (selectedSurface?.kind !== 'table' || selectedArrayCell < 0) return;
    selectArrayColumn(selectedArrayCell % Number(selectedSurface.xCount));
  };
  selectRangeButton.onclick = () => {
    if (selectedArrayCell < 0) return;
    arrayRangeSelectArmed = !arrayRangeSelectArmed;
    if (arrayRangeSelectArmed) selectedArrayAnchor = selectedArrayCell;
    updateArraySelectionControls();
  };
  selectAllButton.onclick = selectAllArrayCells;
  selectClearButton.onclick = () => {
    arrayRangeSelectArmed = false;
    clearArraySelection();
  };
  applySelectionButton.onclick = stageArraySelectionEdit;
  regionValue.addEventListener('keydown', event => { if (event.key === 'Enter') stageArraySelectionEdit(); });
  writeAllButton.onclick = writeAllDrafts;
  saveChangesButton.onclick = saveTuningToEcu;
  arrayInput.addEventListener('keydown', event => { if (event.key === 'Enter') stageArrayCellEdit(); });
  clearChanges.onclick = () => {
    draft = {};
    arrayDraft = {};
    bitDraft = {};
    saveDraft();
    saveArrayDraft();
    saveBitDraft();
    updateChangeCount();
    renderChanges();
    render();
    renderArrayGrid();
  };
  if (page.classList.contains('active')) {
    document.body.classList.add('t4tw-tuner-active');
    setTimeout(loadWorkspaceForActivation, 0);
    setTimeout(renderTunerTelemetry, 0);
  }
})();
