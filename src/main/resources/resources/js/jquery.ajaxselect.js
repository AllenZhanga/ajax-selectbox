(function($) {

    var methods = {
        init: function(options) {
            return this.each(function() {
                var element = $(this);
                var chosenXhr = null;
                var timer = null;
                var selectoptions = [];
                var ajaxcalls = [];

                // check for settings
                var readOnly = false;
                if (options && options.readOnly) {
                    element.attr("readonly", "true");
                    readOnly = true;
                }
                
                var width = "40%";
                if (options && options.width !== undefined &&  options.width !== "") {
                    width = options.width;
                }
                if ($(window).width() <= 767) {
                    width = "100%";
                }

                if ($("body").hasClass("rtl")) {
                    $(element).addClass("chosen-rtl");
                }
                
                var getPlaceholder = function() {
                    var placeholder = "";
                    if ($(element).find("option[value=\"\"]").length > 0) {
                        placeholder = $(element).find("option[value=\"\"]").text();
                        $(element).find("option[value=\"\"]").text("");
                    }
                    if (placeholder === "") {
                        placeholder = " ";
                    }
                    return placeholder;
                };
                
                $(element).find("options").each(function(){
                    selectoptions.push($(this).val());
                });
                
                $(element).chosen({width: width, placeholder_text : getPlaceholder()});
                
                if (!readOnly) {
                    var chosenContainer = $(element).next('.chosen-container');
                    
                    var getValues = function (name) {
                        //get enabled input field oni
                        var el = $('[name=' + name + ']').filter("input[type=hidden]:not([disabled=true]), :enabled, [disabled=false]");
                        var values = new Array();

                        if ($(el).is("select")) {
                            el = $(el).find("option:selected");
                        } else if ($(el).is("input[type=checkbox], input[type=radio]")) {
                            el = $(el).filter(":checked");
                        } 

                        $(el).each(function() {
                            values.push($(this).val());
                        });

                        return values;
                    };
                    
                    //render the options and update the autocomplate list
                    var renderOptions = function(field, keyword, data, values) {
                        var defaultValues = [];
                        var hasValue = false;
                        var hasChanges = false;
                        $(element).empty();
                        for (var i=0, len=data.length; i < len; i++) {
                            selectoptions = [];
                            if ($.inArray(data[i].value, selectoptions) === -1) {
                                var selected = "";
                                if ($.inArray(data[i].value, values) !== -1) {
                                    selected = "selected=\"selected\"";
                                    hasValue = true;
                                }
                                if (data[i].selected !== undefined && data[i].selected.toLowerCase() === "true") {
                                    defaultValues.push(data[i].value);
                                }
                                var option = $("<option "+selected+" >"+UI.escapeHTML(data[i].label)+"</option>");
                                option.attr("value", data[i].value);
                                $(element).append(option);
                                selectoptions.push(data[i].value);
                                hasChanges = true;
                            }
                        }
                        if (hasChanges && defaultValues.length > 0 && !hasValue) {
                            for (var dv in defaultValues) {
                                $(element).find("option[value='"+defaultValues[dv]+"']").attr("selected", "selected");
                            }
                        }
                        if (hasChanges && !$(element).is(".section-visibility-disabled")) {
                            $('[name='+options.paramName+']:not(form):not(.section-visibility-disabled)').trigger("change");
                        }

                        $(field).val(keyword);
                        $(field).css("width", "auto");
                    };
                        
                    var updateOptions = function(keyword, timeout, field) {
                        var values = getValues(options.paramName);
                        var valueStr = "";
                        if (values !== null && values !== undefined) {
                            if (Array.isArray(values)) {
                                valueStr = values.join(";");
                            } else {
                                valueStr = values;
                            }
                        }
                        
                        timer = setTimeout(function() {
                            var params = "";
                            if (options.requestParams !== undefined) {
                                params = FormUtil.getFieldsAsUrlQueryString(options.requestParams, $(this).closest("div.subform-container"));

                                if (params !== "") {
                                    params = "?" + params; 
                                }
                            }
                            
                            if (ajaxcalls[params + "|" + keyword] !== undefined){ //if options of a keyword is available, just use it
                                renderOptions(field, keyword, ajaxcalls[params + "|" + keyword], values);
                                return;
                            }
                            
                            if (chosenXhr) {
                                chosenXhr.abort();
                            }
                            
                            chosenXhr = $.ajax({
                                dataType: "json",
                                url: options.contextPath + "/web/json/app/"+options.appId+"/"+options.appVersion+"/plugin/org.joget.marketplace.AjaxSelectbox/service" + params,
                                method: "POST",
                                data: {
                                    _paramName: options.paramName,
                                    _n: options.nonce,
                                    _listId: options.listId,
                                    _idField: options.idField,
                                    _displayField: options.displayField,
                                    _allowEmpty: options.allowEmpty,
                                    _values : valueStr,
                                    _keyword : keyword
                                },
                                success: function(data){
                                    ajaxcalls[params + "|" + keyword] = data; //store the options of a keyword for later used
                                    renderOptions(field, keyword, data, values);
                                    chosenXhr = null;
                                }
                            });
                        }, timeout);
                    };
                    
                    $(chosenContainer).find(".search-field > input, .chosen-search > input").bind('keyup', function() {
                        var msg, untrimmed_val, val;
                        untrimmed_val = $(this).val();
                        val = $.trim($(this).val());
                        
                        msg = val.length < options.minTermLength ? options.keepTypingMsg : options.lookingForMsg;
                        $(chosenContainer).find('.no-results').text(msg);
                        
                        if (val === $(this).data('prevVal')) {
                            return false;
                        }
                        $(this).data('prevVal', val);
                        
                        if (timer !== null && timer !== undefined) {
                            clearTimeout(timer);
                            timer = null;
                        }
                        
                        if (val.length < options.minTermLength) {
                            //remove the non selected option when keyword length is 0 to clean the options.
                            if (val.length === 0) {
                                var el = $('[name=' + options.paramName + ']').filter("input[type=hidden]:not([disabled=true]), :enabled, [disabled=false]");
                                if ($(el).is("select")) {
                                    $(el).find("option:not(:selected)").remove();
                                } else if ($(el).is("input[type=checkbox], input[type=radio]")) {
                                    $(el).filter(":not(:checked)").remove();
                                } 
                                $(element).append('<option value=""></option>');
                                $(element).trigger("chosen:updated");
                            }
                            return false;
                        }
                        
                        updateOptions(val, options.afterTypeDelay, this);
                    });
                    
                    $(element).on("change", function (){
                        $(".chosen-select").chosen({placeholder_text: getPlaceholder()}); 
                        if ($(element).val() === "") {
                            if (!$(chosenContainer).find('.chosen-drop').hasClass("reseted")) {
                                updateOptions("", options.afterTypeDelay, $(chosenContainer).find('.chosen-search > input'));
                                $(chosenContainer).find('.chosen-drop').addClass('reseted');
                            }
                        } else {
                            $(chosenContainer).find('.chosen-drop').removeClass('reseted');
                        }
                        $(element).trigger("chosen:updated");
                    });
                    $(element).on("jsection:hide", function(){
                        $(element).trigger("chosen:updated");
                    });
                    $(element).on("jsection:show", function(){
                        $(element).trigger("chosen:updated");
                    });
                    setTimeout(function(){
                        $(element).trigger("chosen:updated");
                    }, 200);
                } else {
                    $(element).prop('disabled', true).trigger("chosen:updated").prop('disabled', false);
                }
                
                var hidden = $(element).closest(".ui-screen-hidden");
                $(hidden).find(".chosen-container").insertAfter(hidden);
            });
        }
    };

    $.fn.ajaxselect = function(method) {

        if (methods[method]) {
            return methods[method].apply(this, Array.prototype.slice.call(arguments, 1));
        } else if (typeof method === 'object' || !method) {
            return methods.init.apply(this, arguments);
        } else {
            $.error('Method ' + method + ' does not exist on jQuery.multiselect');
        }

    };

})(jQuery);
