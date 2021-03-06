Ext.define('SD.model.Subject', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'id',         type: 'int',   useNull: true},
        {name: 'mutable_id', type: 'int',   useNull: true},
        {name: 'phone_num',  type: 'string'},
        {name: 'first_name', type: 'string'},
        {name: 'last_name',  type: 'string'},
        {name: 'device_id',  type: 'string'},
        {name: 'is_inactive', type: 'boolean'}
    ],
    validations: [
        {type: 'presence',  field: 'first_name'},
        {type: 'presence',  field: 'last_name'},
        {type: 'presence',  field: 'device_id'},
        {type: 'presence',  field: 'phone_num'}
    ],
    proxy: {
        type: 'rest',
        url : '/rest/subjects'
    }
});