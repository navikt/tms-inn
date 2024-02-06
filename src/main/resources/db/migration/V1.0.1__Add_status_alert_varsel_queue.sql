alter table alert_varsel_queue
    add column varsel_lest boolean;

alter table alert_varsel_queue
    add column status_ekstern text;


alter table alert_varsel_queue
    add column varselId text;

create index varsel_id_idx on alert_varsel_queue (varselId);