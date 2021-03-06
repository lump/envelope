--
-- $Id: bootstrap-mysql.sql,v 1.7 2010/07/28 04:25:04 troy Exp $
--

-- this can be run like this:
-- mysql -u root -pPassword < bootstrap-mysql.sql

drop database if exists envelope;
create database envelope;
connect envelope;

create table budgets (
  `id` int not null auto_increment primary key,
  `stamp` timestamp not null default current_timestamp,
  `name` varchar(64) not null,
  unique index name_index(`name`)
)ENGINE=INNODB;
insert into budgets values(0,null,'Test Budget');
update budgets set id = 0;
alter table budgets auto_increment = 0;


create table accounts (
  `id` int NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `budget` int not null,
  `name` varchar(64) not null,
  `type` enum('Debit','Credit','Loan') not null default 'Debit',
  `rate` double not null default 0.0,
  `ceiling` double not null default 0.0,
  unique index name_type (`name`,`type`),
  constraint accounts_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into accounts values (0, null, 0, 'Guest''s Checking','Debit', '0.0', '0.0');
update accounts set id = 0;
alter table accounts auto_increment = 0;

-- create table allocation_settings (
--  `id` int not null auto_increment primary key,
--  `stamp` timestamp not null default current_timestamp,
--  `budget` int not null,
--  `name` varchar(64) not null,
--  `type` enum('Reimbursement','Weekly_Payday','Biweekly_Payday','Semimonthly_Payday','Monthly_Payday') not null,
--  `reference_date` date,
--  unique index budget_name(`budget`,`name`),
--  constraint allocation_settings_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
-- )ENGINE=INNODB;
-- insert into allocation_settings values(null,null,0,'Guest''s Main Job','Semimonthly Payday',now());
-- update allocation_settings set id=0;
-- alter table allocation_settings auto_increment = 0;

create table categories (
  `id` int NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `account` int not null,
  `name` varchar(64) NOT NULL default '',
  unique index budget_name (`account`,`name`),
  constraint categories_accounts foreign key (account) references accounts(id) on update cascade on delete restrict
)ENGINE=INNODB;

-- create table category_allocation_settings (
--  `id` int not null auto_increment primary key,
--  `stamp` timestamp not null default current_timestamp,
--  `allocation_setting` int not null,
--  `category` int not null,
--  `allocation` double not null default '0.0',
--  `allocation_type` enum('ppp','fpp','fpm') not null default 'ppp',
--  `auto_deduct` tinyint(1) not null default '0',
--  constraint cat_alloc_settings_categories foreign key (category) references categories(id) on update cascade on delete restrict,
--  constraint cat_alloc_settings_alloc_settings foreign key (allocation_setting) references allocation_settings(id) on update cascade on delete restrict
-- )ENGINE=INNODB;

create table allocation_presets (
  `id` int NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `budget` int not null,
  `name` varchar(64) NOT NULL,
  `category` int not null,
  `allocation` double not null default '0.0',
  `allocation_type` enum('percent','fixed') not null default 'fixed',
  `auto_deduct` tinyint(1) not null default '0',
  constraint presets_categoryies_categories foreign key (category) references categories(id) on update cascade on delete restrict,
  constraint presets_budget foreign key (budget) references budgets(id) on update cascade on delete restrict
)ENGINE=INNODB;

insert into categories values(null, null, 0, 'Tithing');
update categories set id = 0;
alter table categories auto_increment = 0;
insert into allocation_presets values (null, null, 0, "Pay Day", 0, 0.1, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, 0, '.1', 'ppp', 0);
insert into categories values(null, null, 0, 'Water');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 22.5, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),22.5,'fpm',0);
insert into categories values(null, null, 0, 'Subscriptions');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 2, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),2,'fpp',0);
insert into categories values(null, null, 0, 'Travel');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'State Tax');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 3.76, 'percent',1);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),3.76,'ppp',1);
insert into categories values(null, null, 0, 'Social Security');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 90, 'fixed',1);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),90,'fpp',1);
insert into categories values(null, null, 0, 'Sewer');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 2.5, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),2.5,'fpm',0);
insert into categories values(null, null, 0, 'Restaurants');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 10, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),10,'fpp',0);
insert into categories values(null, null, 0, 'Recreation');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 5, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),5,'fpp',0);
insert into categories values(null, null, 0, 'Phone');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 30, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),30,'fpm',0);
insert into categories values(null, null, 0, 'Other Taxes and Fees');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'Mortgage');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 600, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),600,'fpm',0);
insert into categories values(null, null, 0, 'Memberships');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'Medicare');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 30, 'fixed',1);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),30,'fpp',1);
insert into categories values(null, null, 0, 'Medical Insurance');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 100, 'fixed',1);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),100,'fpp',1);
insert into categories values(null, null, 0, 'Medical');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 46, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),46,'fpm',0);
insert into categories values(null, null, 0, 'Media');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'fpp',0);
insert into categories values(null, null, 0, 'Home');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 40, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),40,'fpm',0);
insert into categories values(null, null, 0, 'Hobbies');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'Haircuts');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 4, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),4,'fpm',0);
insert into categories values(null, null, 0, 'Groceries');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 200, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),200,'fpp',0);
insert into categories values(null, null, 0, 'Gifts');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 20, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),20,'fpp',0);
insert into categories values(null, null, 0, 'Gasoline');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 30, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),30,'fpp',0);
insert into categories values(null, null, 0, 'Gas');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 50, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),50,'fpm',0);
insert into categories values(null, null, 0, 'Furniture');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 7, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),7,'ppp',0);
insert into categories values(null, null, 0, 'Federal Tax');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',1);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',1);
insert into categories values(null, null, 0, 'Fast Offering');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'Electronics');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 0, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),0,'ppp',0);
insert into categories values(null, null, 0, 'Computer');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 15, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),15,'fpm',0);
insert into categories values(null, null, 0, 'Clothes');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 15, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),15,'fpp',0);
insert into categories values(null, null, 0, 'Car Payment');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 6.25, 'percent',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),6.25,'ppp',0);
insert into categories values(null, null, 0, 'Car Maintenance');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 12, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),12,'fpm',0);
insert into categories values(null, null, 0, 'Car Insurance');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 266.68, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),266.68,'fpm',0);
insert into categories values(null, null, 0, 'Electricity');
insert into allocation_presets values (null, null, 0, "Pay Day", (select id from categories where id = last_insert_id()), 70, 'fixed',0);
-- insert into category_allocation_settings values (null, null, 0, (select id from categories where id = last_insert_id()),70,'fpm',0);

-- create table tags (
--  `id` int(11) NOT NULL auto_increment primary key,
--  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
--  `budget` int not null,
--  `name` varchar(64) not null,
--  constraint tags_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
-- )ENGINE=INNODB;
-- insert into tags values (null, null, 0, 'Adjustment');
-- update tags set id = 0;
-- alter table tags auto_increment = 0;

create table transactions (
  `id` int(11) NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `date` date not null,
  `entity` varchar(128) not null,
  `description` varchar(255) not null,
  `reconciled` tinyint(4) not null default '0',
  `transfer` tinyint(4) not null default '0'
)ENGINE=INNODB;
insert into transactions values (null, null, now(), 'Beginning Balance', 'Starting Balance', 0, 0);
update transactions set id = 0;
alter table transactions auto_increment = 0;
insert into transactions values (1, null, now(), 'Beginning Balance', 'Starting Balance', 0, 0);

-- create table allocation_tag (
--  `allocation` int(11) NOT NULL,
--  `tag` int(11) NOT NULL
-- )ENGINE=INNODB;
-- insert into allocation_tag values (0, 0);
-- insert into allocation_tag values (1, 0);

create table allocations (
  `id` int(11) NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `category` int(11) not null,
  `transaction` int(11) not null,
  `amount` decimal(8,2) not null,
  constraint category foreign key (category) references categories(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  constraint transaction foreign key (transaction) references transactions(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into allocations values (null, null, 0, 0, '5.3');
insert into allocations values (null, null, 0, 0, '3.2');
insert into allocations values (null, null, 0, 1, '2.2');
insert into allocations values (null, null, 0, 1, '4.4');
insert into allocations values (null, null, 0, 1, '6.6');

create table `users` (
  `id` int(11) NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `budget` int NOT NULL,
  `name` varchar(64) not null,
  `real_name` varchar(64) default NULL,
  `crypt_password` varchar(34) default NULL,
  `permissions` int(11) default NULL,
  `public_key` text,
  UNIQUE index `name` (`name`),
  constraint users_budget foreign key (budget) references budgets(id) on update cascade on delete restrict
)ENGINE=INNODB;
insert into users values (null, null,0,'admin','Admin Account','$1$INZEtT3S$D81Kp34n4Oea5Rs97lPOq0',7,NULL);
update users set id = 0;
alter table users auto_increment = 0;
insert into users values (null, null,0,'guest','Guest Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',3,NULL);

grant all on envelope.* to budget identified by 'tegdub';
