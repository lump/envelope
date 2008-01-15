--
-- $Id: bootstrap.sql,v 1.8 2008/01/15 04:10:02 troy Exp $
--

drop table if exists users;
drop table if exists allocations;
drop table if exists transactions;
drop table if exists categories;
drop table if exists accounts;
drop table if exists incomes;
drop table if exists allocation_tag;
drop table if exists tags;
drop table if exists budgets;

create table budgets (
  `id` int not null auto_increment primary key,
  `stamp` timestamp not null default current_timestamp,
  `name` varchar(64) not null,
  unique index name_index(`name`)
)ENGINE=INNODB;
insert into budgets values(0,null,'Test Budget');
update budgets set id = 0;
alter table budgets auto_increment = 0;

create table incomes (
  `id` int not null auto_increment primary key,
  `stamp` timestamp not null default current_timestamp,
  `budget` int not null,
  `name` varchar(64) not null,
  `type` enum('Reimbursement','Weekly_Payday','Biweekly_Payday','Semimonthly_Payday','Monthly_Payday') not null,
  `reference_date` date,
  unique index budget_name(`budget`,`name`),
  constraint incomes_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into incomes values(null,null,0,'Guest''s Main Job','Semimonthly Payday',now());
update incomes set id=0;
alter table incomes auto_increment = 0;

create table accounts (
  `id` int NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `budget` int not null,
  `name` varchar(64) not null,
  `type` enum('Debit','Credit','Loan') not null default 'Debit',
  `rate` double not null default 0.0,
  `limit` double not null default 0.0,
  unique index name_type (`name`,`type`),
  constraint accounts_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into accounts values (0, null, 0, 'Guest''s Checking','Debit', '0.0', '0.0');
update accounts set id = 0;
alter table accounts auto_increment = 0;

create table categories (
  `id` int NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `account` int not null,
  `name` varchar(64) NOT NULL default '',
  `allocation` double not null default '0.0',
  `allocation_type` enum('ppp','fpp','fpm') not null default 'ppp',
  `auto_deduct` tinyint(1) not null default '0',
  unique index budget_name (`account`,`name`),
  constraint categories_accounts foreign key (account) references accounts(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into categories values(null, null, 0, 'Tithing' , '.1', 'ppp', 0);
update categories set id = 0;
alter table categories auto_increment = 0;
insert into categories values(null, null, 0, 'Water',22.5,'fpm',0);
insert into categories values(null, null, 0, 'Travel',0,'ppp',0);
insert into categories values(null, null, 0, 'Subscriptions',2,'fpp',0);
insert into categories values(null, null, 0, 'State Tax',3.76,'ppp',1);
insert into categories values(null, null, 0, 'Social Security',90,'fpp',1);
insert into categories values(null, null, 0, 'Sewer',2.5,'fpm',0);
insert into categories values(null, null, 0, 'Restaurants',10,'fpp',0);
insert into categories values(null, null, 0, 'Recreation',5,'fpp',0);
insert into categories values(null, null, 0, 'Phone',30,'fpm',0);
insert into categories values(null, null, 0, 'Other Taxes and Fees',0,'ppp',0);
insert into categories values(null, null, 0, 'Mortgage',600,'fpm',0);
insert into categories values(null, null, 0, 'Memberships',0,'ppp',0);
insert into categories values(null, null, 0, 'Medicare',30,'fpp',1);
insert into categories values(null, null, 0, 'Medical Insurance',100,'fpp',1);
insert into categories values(null, null, 0, 'Medical',46,'fpm',0);
insert into categories values(null, null, 0, 'Media',0,'fpp',0);
insert into categories values(null, null, 0, 'Home',40,'fpm',0);
insert into categories values(null, null, 0, 'Hobbies',0,'ppp',0);
insert into categories values(null, null, 0, 'Haircuts',4,'fpm',0);
insert into categories values(null, null, 0, 'Groceries',200,'fpp',0);
insert into categories values(null, null, 0, 'Gifts',20,'fpp',0);
insert into categories values(null, null, 0, 'Gasoline',30,'fpp',0);
insert into categories values(null, null, 0, 'Gas',50,'fpm',0);
insert into categories values(null, null, 0, 'Furniture',7,'ppp',0);
insert into categories values(null, null, 0, 'Federal Tax',0,'ppp',1);
insert into categories values(null, null, 0, 'Fast Offering',0,'ppp',0);
insert into categories values(null, null, 0, 'Electronics',0,'ppp',0);
insert into categories values(null, null, 0, 'Computer',15,'fpm',0);
insert into categories values(null, null, 0, 'Clothes',15,'fpp',0);
insert into categories values(null, null, 0, 'Car Payment',6.25,'ppp',0);
insert into categories values(null, null, 0, 'Car Maintenance',12,'fpm',0);
insert into categories values(null, null, 0, 'Car Insurance',266.68,'fpm',0);
insert into categories values(null, null, 0, 'Electricity',70,'fpm',0);

create table tags (
  `id` int(11) NOT NULL auto_increment primary key,
  `stamp` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `budget` int not null,
  `name` varchar(64) not null,
  constraint tags_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
)ENGINE=INNODB;
insert into tags values (null, null, 0, 'Adjustment');
update tags set id = 0;
alter table tags auto_increment = 0;

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

create table allocation_tag (
  `allocation` int(11) NOT NULL,
  `tag` int(11) NOT NULL
)ENGINE=INNODB;
insert into allocation_tag values (0, 0);
insert into allocation_tag values (1, 0);

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
  `public_key` blob,
  UNIQUE index `name` (`name`),
  constraint users_budget foreign key (budget) references budgets(id) on update cascade on delete restrict
)ENGINE=INNODB;
insert into users values (null, null,0,'admin','Admin Account','$1$INZEtT3S$D81Kp34n4Oea5Rs97lPOq0',7,NULL);
update users set id = 0;
alter table users auto_increment = 0;
insert into users values (null, null,0,'guest','Guest Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',3,NULL);

grant all on envelope.* to budget identified by 'tegdub';
