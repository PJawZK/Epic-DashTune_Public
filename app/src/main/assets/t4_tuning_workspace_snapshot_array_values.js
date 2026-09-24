/* TUNER_UI_SNAPSHOT_ARRAY_VALUES_V5
 * The complete native TuneSnapshot has already been decoded into the semantic workspace.
 * Reuse those embedded semantic values for live tables/curves and for the permanent saved Tuner
 * project instead of requiring menu visits or additional per-array USB/native detail requests.
 * An INI-only definition deliberately carries structure without inventing values.
 */
  const t4SnapshotArrayDetailBase=getSemanticArrayDetail;

  function t4EmbeddedSnapshotArrayDetail(name,data=workspace){
    const key=String(name||'').trim();
    if(!key||data?.status!=='ready')return null;
    const array=(data.arrays||[]).find(item=>String(item?.name||'')===key);
    if(!array||!Array.isArray(array.values))return null;
    const elementCount=Number(array.elementCount||0);
    if(elementCount<=0||array.values.length!==elementCount)return null;
    const values=array.values.map(Number);
    if(values.some(value=>!Number.isFinite(value)))return null;
    const saved=data?.savedProject===true;
    return{
      status:'ready',
      capability:saved?'SAVED_PROJECT':'READ_ONLY',
      source:saved?'SAVED_TUNE_SNAPSHOT':'LIVE_TUNE_SNAPSHOT',
      generation:Number(data?.generation||0),
      profileFingerprint:String(data?.profileFingerprint||''),
      tuneFingerprint:String(data?.tuneFingerprint||''),
      name:key,
      dimensions:Array.isArray(array.dimensions)?array.dimensions.map(Number):[],
      elementCount,
      unit:String(array.unit||''),
      low:array.low==null?null:Number(array.low),
      high:array.high==null?null:Number(array.high),
      digits:Number(array.digits||0),
      valueAvailable:true,
      values
    };
  }

  function t4IniStructureArrayDetail(name,data=workspace){
    if(data?.status!=='ready'||data?.definitionOnly!==true)return null;
    const key=String(name||'').trim();
    if(!key)return null;
    const array=(data.arrays||[]).find(item=>String(item?.name||'')===key);
    if(!array)return null;
    const elementCount=Number(array.elementCount||0);
    if(!Number.isInteger(elementCount)||elementCount<=0)return null;
    return{
      status:'ready',
      capability:'INI_STRUCTURE',
      source:'INI_DEFINITION',
      generation:Number(data?.generation||0),
      profileFingerprint:String(data?.profileFingerprint||''),
      tuneFingerprint:String(data?.tuneFingerprint||''),
      name:key,
      dimensions:Array.isArray(array.dimensions)?array.dimensions.map(Number):[],
      elementCount,
      unit:String(array.unit||''),
      low:array.low==null?null:Number(array.low),
      high:array.high==null?null:Number(array.high),
      digits:Number(array.digits||0),
      valueAvailable:false,
      writeAllowed:false,
      reason:'INI table/curve structure is available; cell values require a matching complete TuneSnapshot.',
      values:Array.from({length:elementCount},()=>Number.NaN)
    };
  }

  getSemanticArrayDetail=function(name){
    const embedded=t4EmbeddedSnapshotArrayDetail(name,workspace);
    if(embedded)return embedded;
    const structure=t4IniStructureArrayDetail(name,workspace);
    if(structure)return structure;
    if(workspace?.definitionOnly===true){
      return{
        status:'not_ready',
        capability:'INI_STRUCTURE',
        source:'INI_DEFINITION',
        name:String(name||''),
        reason:'This array is not structurally available in the current INI definition.'
      };
    }
    return t4SnapshotArrayDetailBase(name);
  };
