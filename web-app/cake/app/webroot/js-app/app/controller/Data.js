Ext.define('SD.controller.Data', {
    extend: 'Ext.app.Controller',
    models: ['Answer', 'Location', 'Call', 'StatusChange', 'SurveyTaken'],
    stores: ['Answers', 'Locations', 'Calls', 'StatusChanges', 'SurveysTaken'],
    refs: [
        {ref: 'mainTabs', selector: 'mainTabs' },
        {ref: 'dataTab', selector: '#dataTab' },
        {ref: 'subjectFilter', selector: '#subjectFilter' },
        {ref: 'surveyFilter', selector: '#surveyFilter' }
    ],
    init: function() {
        var me = this;
        me.control({
            '#subjectFilter': {
                selectionchange: me.filterAnswers
            },
            '#surveyFilter': {
                selectionchange: me.filterAnswers
            },
            '#callsTab': {
                activate: function() { me.loadIfEmpty('Calls'); }
            },
            '#locationsTab': {
                activate: function() { me.loadIfEmpty('Locations'); }
            },
            '#answersTab': {
                activate: function() { me.loadIfEmpty('Answers'); }
            },
            '#statuschangesTab': {
                activate: function() { me.loadIfEmpty('StatusChanges'); }
            },
            '#surveystakenTab': {
                activate: function() { me.loadIfEmpty('SurveysTaken'); }
            }
        })
    },
    onLaunch: function() {
        this.getMainTabs().setActiveTab('surveysTab');
//        this.getDataTab().setActiveTab('callsTab');
    },
    loadIfEmpty: function(storeName) {
        var store = Ext.getStore(storeName);
        if (!store.isLoading() && store.count() == 0)
        store.load();
    },
    filterAnswers: function() {
        var answers = this.getAnswersStore(),
            filters = [],
            survey = this.getSurveyFilter().getSelectionModel().getSelection()[0],
            subject = this.getSubjectFilter().getSelectionModel().getSelection()[0];
        answers.clearFilter();
        if (survey)
            filters.push({ property: 'survey_id', value: survey.getId(), exactMatch: true });
        if (subject)
            filters.push({ property: 'subject_id', value: subject.getId(), exactMatch: true });
        if (!Ext.isEmpty(filters))
            answers.filter(filters)
    }
});