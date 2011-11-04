
define(
  ['./visualization/models/VisualizationProfiles'], 
  (VisualizationProfiles) ->

    class DataBrowserSettings

      LABEL_PROPERTIES_KEY : 'databrowser.labelProperties'
      LABEL_PROPERTIES_DEFAULT : ['name']
      
      VIZ_PROFILES_KEY : 'databrowser.visualization.profiles'
      VIZ_PROFILES_DEFAULT : [{"id":0,"name":"Default profile","builtin":true,"styleRules":[]},{"name":"Cineasts","styleRules":[{"target":"node","style":{"type":"node","shape":"box","shapeColor":"#000000","labelFont":"monospace","labelSize":10,"labelColor":"#eeeeee","labelPattern":"User: {prop.name}"},"order":null,"id":3,"filters":[{"method":"exists","propertyName":"login","id":1,"type":"d"}]},{"target":"node","style":{"type":"node","shape":"box","shapeColor":"rgb(135, 73, 135)","labelFont":"monospace","labelSize":10,"labelColor":"#eeeeee","labelPattern":"Actor: {prop.name}"},"order":1,"id":2,"filters":[{"method":"exists","propertyName":"name","id":1,"type":"d"}]},{"target":"node","style":{"type":"node","shape":"box","shapeColor":"rgb(60, 130, 124)","labelFont":"monospace","labelSize":10,"labelColor":"#eeeeee","labelPattern":"Movie: {prop.title}"},"order":2,"id":1,"filters":[{"method":"exists","propertyName":"title","id":1,"type":"d"}]}],"id":1}]

      CURRENT_VIZ_PROFILE_KEY : 'databrowser.visualization.currentProfile'

      constructor : (@settings) ->
        # Pass

      getLabelProperties : () ->
        s = @settings.get(@LABEL_PROPERTIES_KEY)
        if s and _(s).isArray()
          s
        else @LABEL_PROPERTIES_DEFAULT
        
      setLabelProperties : (properties) ->
        attr = {}
        attr[@LABEL_PROPERTIES_KEY] = properties
        @settings.set(attr)
        @settings.save()
      
      labelPropertiesChanged : (callback) ->
        @settings.bind 'change:' + @LABEL_PROPERTIES_KEY,  callback
        
      getVisualizationProfiles : () ->
        prof = @settings.get @VIZ_PROFILES_KEY, VisualizationProfiles, @VIZ_PROFILES_DEFAULT
        if prof.size() == 0
          @settings.remove @VIZ_PROFILES_KEY
          prof = @settings.get @VIZ_PROFILES_KEY, VisualizationProfiles, @VIZ_PROFILES_DEFAULT
        prof
        
      getCurrentVisualizationProfile : () ->
        id = @settings.get @CURRENT_VIZ_PROFILE_KEY
        profiles = @getVisualizationProfiles()
        if id? and profiles.get id
          return profiles.get id
        else
          return profiles.first()
      
      setCurrentVisualizationProfile : (id) ->
        id = id.id if id.id?
        @settings.set @CURRENT_VIZ_PROFILE_KEY, id
        
      onCurrentVisualizationProfileChange : (cb) ->
        @settings.bind "change:#{@CURRENT_VIZ_PROFILE_KEY}", cb
      
)
