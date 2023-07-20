package org.joget.marketplace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.lib.SelectBox;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPalette;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormReferenceDataRetriever;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONException;

public class AjaxSelectbox extends SelectBox implements PluginWebSupport, FormBuilderPaletteElement, FormReferenceDataRetriever {
    public static String MESSAGE_PATH = "message/AjaxSelectbox";
    private String idField;
    
    @Override
    public String getName() {
        return "AjaxSelectbox";
    }

    @Override
    public String getVersion() {
        return "7.0.5";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage(getName() + ".desc", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<label class='label'>"+getLabel()+"</label><input type='textfield' value='Type to Select' style='color:silver' />";
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage(getName() + ".label", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getPropertyOptions() {
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/ajaxselectbox.json", null, true, MESSAGE_PATH);
        return json;
    }
    
    @Override
    public String getFormBuilderCategory() {
        return FormBuilderPalette.CATEGORY_CUSTOM;
    }

    @Override
    public int getFormBuilderPosition() {
        return 350;
    }
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "ajaxSelectbox.ftl";

        // set value
        String[] valueArray = FormUtil.getElementPropertyValues(this, formData);
        List<String> values = Arrays.asList(valueArray);
        dataModel.put("values", values);

        // set options
        Collection<Map> optionMap = getOptionMap(valueArray, null, formData);
        dataModel.put("options", optionMap);

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        dataModel.put("appId", appDef.getAppId());
        dataModel.put("appVersion", appDef.getVersion().toString());

        String paramName = FormUtil.getElementParameterName(this);
        
        String nonceList = SecurityUtil.generateNonce(new String[]{"AjaxSelectbox", appDef.getAppId(), appDef.getVersion().toString(), paramName, getPropertyString("listId")}, 1);
        dataModel.put("nonceList", nonceList);
        
        Object requestParamsProperty = getProperty("requestParams");
        if (requestParamsProperty != null && requestParamsProperty instanceof Object[]) {
            String requestJson = "[";
            for (Object param : ((Object[]) requestParamsProperty)) {
                Map paramMap = ((Map)param);

                if (requestJson.length() > 1) {
                    requestJson += ",";
                }
                requestJson += "{";
                requestJson += "param:'" + paramMap.get("param") + "',";
                requestJson += "field:'" + paramMap.get("field") + "',";
                requestJson += "defaultValue:'" + paramMap.get("defaultValue") + "'";
                requestJson += "}";
            }
            requestJson += "]";

            if (requestJson.length() > 2) {
                dataModel.put("requestParams", requestJson);
            }
        }

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }
    
    public Collection<Map> getOptionMap(String[] valueArray, String keyword, FormData formData){
        Map<String, String> options = new LinkedHashMap<String, String>();
        
        if ("true".equalsIgnoreCase(getPropertyString("allowEmpty"))) {
            options.put("", getPropertyString("placeholder"));
        }
        
        if (keyword != null && !keyword.isEmpty()) {
            addOptions(options, null, formData, keyword, null);
        }  
        if (valueArray != null && valueArray.length > 0 && !(valueArray.length == 1 && valueArray[0].isEmpty())) {
            addOptions(options, valueArray, formData, null, null);
        }
        
        if ((keyword == null || keyword.isEmpty())) {
            int number = 0;
            if (!getPropertyString("defaultOptions").isEmpty()) {
                try {
                    number = Integer.parseInt(getPropertyString("defaultOptions"));
                } catch (Exception e) {
                }
            }
            addOptions(options, null, formData, null, number);
        }
        
        Collection<Map> results = new ArrayList<Map>();
        for (String v : options.keySet()) {
            Map m = new HashMap();
            m.put(FormUtil.PROPERTY_VALUE, v);
            m.put(FormUtil.PROPERTY_LABEL, options.get(v));
            results.add(m);
        }
        
        return results;
    }
    
    protected void addOptions(Map<String, String> options, String[] valueArray, FormData formData, String keyword, Integer size) {
        DataListCollection rows = getDataListCollection(valueArray, formData, keyword, size);
        if (rows != null && !rows.isEmpty()) {
            String displayField = getPropertyString("displayField");
            if (idField != null && displayField != null) {
                Iterator i = rows.iterator();
                while (i.hasNext()) {
                    Object r = i.next();
                    options.put(DataListService.evaluateColumnValueFromRow(r, idField).toString(), DataListService.evaluateColumnValueFromRow(r, displayField).toString());
                }
            }
        }
    }
    
    protected DataListCollection getDataListCollection(String[] valueArray, FormData formData, String keyword, Integer size) {
        DataListCollection rows = null;
        
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(getPropertyString("listId"), appDef);
        String json = datalistDefinition.getJson();

        //populate request params
        Object requestParamsProperty = getProperty("requestParams");
        if (formData != null && requestParamsProperty != null && requestParamsProperty instanceof Object[]) {
            Form form = FormUtil.findRootForm(this);

            for (Object param : ((Object[]) requestParamsProperty)) {
                Map paramMap = ((Map)param);
                String parameter = (String) paramMap.get("param");
                String fieldId = (String) paramMap.get("field");
                String defaultValue = (String) paramMap.get("defaultValue");
                String[] paramValues = null;
                String paramValue = "";

                if (fieldId != null && !fieldId.isEmpty()) {
                    Element field = FormUtil.findElement(fieldId, form, formData);
                    paramValues = FormUtil.getElementPropertyValues(field, formData);
                }

                if (!(paramValues != null && paramValues.length > 0)) {
                    paramValues = new String[]{defaultValue};
                }

                paramValue = FormUtil.generateElementPropertyValues(paramValues);
                json = json.replaceAll(StringUtil.escapeRegex("#requestParam."+parameter+"#"), StringUtil.escapeRegex(paramValue));
            }
        }

        DataListService dataListService = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
        DataList dataList = dataListService.fromJson(json);

        DataListBinder binder = dataList.getBinder();
        idField = getPropertyString("idField");

        if (binder != null) {
            Collection<DataListFilterQueryObject> queries = new ArrayList<DataListFilterQueryObject>();
            DataListFilterQueryObject[] datalistFilter = dataList.getFilterQueryObjects();
            queries.addAll(Arrays.asList(datalistFilter));
            
            if (idField == null || idField.isEmpty()) {
                idField = binder.getPrimaryKeyColumnName();
            }
            
            if (valueArray != null && valueArray.length > 0) {
                List<String> values = Arrays.asList(valueArray);
                
                if (values != null && !values.isEmpty() && !"".equals(values.get(0))) {
                    DataListFilterQueryObject queryObject = new DataListFilterQueryObject();
                    
                    String query = binder.getColumnName(idField) + " in (";
                    for (String v : values) {
                        query += "?,";
                    }
                    query = query.replaceFirst(",$", ")");
                    queryObject.setOperator("AND");
                    queryObject.setQuery(query);
                    queryObject.setValues(values.toArray(new String[0]));

                    queries.add(queryObject);
                }
            }
            
            if (keyword != null && !keyword.isEmpty()) {
                DataListFilterQueryObject queryObject = new DataListFilterQueryObject();
                String query = binder.getColumnName(getPropertyString("displayField")) + " like ?";
                queryObject.setOperator("AND");
                queryObject.setQuery(query);
                queryObject.setValues(new String[]{"%" + keyword + "%"});

                queries.add(queryObject);
            }
            
            rows = binder.getData(dataList, binder.getProperties(), queries.toArray(new DataListFilterQueryObject[0]), null, null, null, size);
        }
        return rows;
    }
    
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = SecurityUtil.validateStringInput(request.getParameter("action"));
        if (action != null && !action.isEmpty()) {
            
        } else {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            
            String param = SecurityUtil.validateStringInput(request.getParameter("_paramName"));
            String listId = SecurityUtil.validateStringInput(request.getParameter("_listId"));
            
            String nonce = request.getParameter("_n");
            if (!SecurityUtil.verifyNonce(nonce, new String[]{"AjaxSelectbox", appDef.getAppId(), appDef.getVersion().toString(), param, listId})) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            //set propertise
            setProperty("listId", listId);
            setProperty("idField", SecurityUtil.validateStringInput(request.getParameter("_idField")));
            setProperty("displayField", SecurityUtil.validateStringInput(request.getParameter("_displayField")));
            setProperty("allowEmpty", SecurityUtil.validateStringInput(request.getParameter("_allowEmpty")));
            
            String valueStr = StringUtil.stripAllHtmlTag(request.getParameter("_values"));
            String[] values = null;
            if (!valueStr.isEmpty()) {
                values = valueStr.split(";");
            }

            String keyword = StringUtil.stripAllHtmlTag(request.getParameter("_keyword"));
            
            Collection<Map> optionMap = getOptionMap(values, keyword, null);
            
            JSONArray jsonArray = new JSONArray();
            if (optionMap != null) {
                for (Map row : optionMap) {
                    jsonArray.put(row);
                }
            }

            try {
                jsonArray.write(response.getWriter());
            } catch (JSONException ex) {
                LogUtil.error(getClass().getName(), ex, "");
            }
        }
    }

    @Override
    public FormRowSet loadFormRows(String[] primaryKeyValues, FormData formData) {
        FormRowSet rowSet = new FormRowSet();
        DataListCollection list = getDataListCollection(primaryKeyValues, formData, null, null);
        if (list != null && !list.isEmpty()) {
            Iterator i = list.iterator();
            while (i.hasNext()) {
                Map item = (Map) i.next();
                FormRow row = new FormRow();
                row.putAll(item);
                rowSet.add(row);
            }
        }
        return rowSet;
    }
}
