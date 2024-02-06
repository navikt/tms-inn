alter table alert_varsel_queue
    add column varsel_lest boolean;

alter table alert_varsel_queue
    add column status_ekstern varchar;


alter table alert_varsel_queue
    add column varselId varchar;

create index varsel_id_idx on alert_varsel_queue (varselId);