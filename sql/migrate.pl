#!/usr/bin/perl

# $Id: migrate.pl,v 1.16 2008/08/27 03:27:51 troy Exp $
#
# migrate troy's existing live envelope database
# requires a fresh database (boostrap.sql)

use DBI;

$|=1;

$dbs = {
  source => {
    database => "budgets",
    port => 3306,
    user => "budget",
    host => "dublan.net",
    password => "DeADFeeDBeeF",
  },
  dest => {
    database => "envelope",
    port => 3306,
    host => "localhost",
    user => "budget",
    password => "tegdub",
  },
};

for my $db (keys %$dbs) {
    my $this = $dbs->{$db};
    my $dsn = "DBI:mysql:database=" . $this->{database} . ";"
      . "host=" . $this->{host} . ";"
      . "port=" . $this->{port};
    $this->{connection} = DBI->connect($dsn, $this->{user}, $this->{password}) or die $!;
    ${db} = $this->{connection};
}

# dump the entire source database into hashes
# for my $table (qw(categories settings transactions users)) {
#   $dbs->{source}->{$table} = [];
#   $sth = $dbs->{source}->{connection}->prepare("select * from $table") or die $source->errstr;
#   $sth->execute or die $sth->errstr;
#   while (my $row = $sth->fetchrow_hashref()) {
#     push @{$dbs->{source}->{$table}}, $row;
#   }
#   $sth->finish;
# }


$dbs->{dest}->{connection}->do("replace into budgets values (null, null, 'Bowman')") 
  or die $dbs->{source}->{connection}->errstr;
($budget_id) = $dbs->{dest}->{connection}->selectrow_array("select id from budgets where id is null")
  or die $dbs->{source}->{connection}->errstr;
print "Budget id: $budget_id\n";

$accounts = {
    Checking => { id => undef, type => 'Debit', rate => 0.005 },
    Savings => { id => undef, type => 'Debit', rate => 0.0141 },
    DSavings => { id => undef, type => 'Debit', rate => 0.0475 },
    Tacoma => { id => undef, type => 'Loan', rate => 0.0 },
};

for my $account (qw(Checking Savings DSavings Tacoma)) {
  my $entry = $accounts->{$account};
  $dbs->{dest}->{connection}->do("replace into accounts values (null, null, ?, ?, ?, ?, 0)", 
                                  undef, $budget_id, $account, $entry->{type}, $entry->{rate});
  ($entry->{id}) = $dbs->{dest}->{connection}->selectrow_array("select id from accounts where id is null")
    or die $dbs->{source}->{connection}->errstr;
  print "$account id: $entry->{id}\n";
}

my %tag_ids = ();
$dsth = $dbs->{dest}->{connection}->prepare("insert into tags (budget, name) values (?, ?)");

$sth = $dbs->{source}->{connection}->prepare("select distinct subcategory from transactions where subcategory is not null order by subcategory")
  or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  next if ($row->{subcategory} =~ /^.*? and .*?$/
           and $row->{subcategory} !~ /^Yard/);

  $dsth->execute($budget_id, $row->{subcategory});
  my ($last_id) = $dbs->{dest}->{connection}->selectrow_array("select id from tags where id is null");
  $tag_ids{$row->{subcategory}} = $last_id;
  print "Inserted tag $budget_id, $row->{subcategory} as $last_id\n";
}
$sth->finish;
$dsth->finish;

$dsth = $dbs->{dest}->{connection}->prepare("
insert into users (budget,name,real_name,crypt_password,permissions)
values (?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$sth = $dbs->{source}->{connection}->prepare("select *,permissions|0 as int_permissions from users where budgetname = 'bowman'")
  or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  $dsth->execute($budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions})
    or die $dsth->errstr;
  print "Inserted user $budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions}\n";
}

# add a test user for this budget
$dsth->execute($budget_id, 'bowmantest', 'Bowman Test Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',7);
$sth->finish;
$dsth->finish;


$dsth = $dbs->{dest}->{connection}->prepare(
  "insert into allocation_settings (budget,name,type,reference_date) values (?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;
$dsth->execute($budget_id, "SOS", 'Biweekly_Payday', '2006-11-30');
$dsth->finish;
print "inserted allocation setting $budget_id, SOS, Biweekly_Payday, 2006-11-30\n";
($allocation_setting_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocation_settings where id is null");

$dsth = $dbs->{dest}->{connection}->prepare("insert into categories (account, name) values (?, ?)")
  or die $dbs->{source}->{connection}->errstr;

$dsth2 = $dbs->{dest}->{connection}->prepare("
insert into category_allocation_settings
(allocation_setting, category, allocation, allocation_type, auto_deduct)
values (?, (select id from categories where id is null), ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;

$sth = $dbs->{source}->{connection}->prepare("select * from categories where budgetname = 'bowman'") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  next if ($row->{category} eq "All Categories");
  my $allocation_amount = $row->{which} eq "fpp"
    ? $row->{fixed_per_paycheck}
    : $row->{which} eq "ppp"
      ? $row->{percent_per_paycheck}
      : $row->{fixed_per_month};

  my $account_id = $accounts->{Checking}->{id};
  if ($row->{category} eq "Savings") { $account_id = $accounts->{Savings}->{id} }
  elsif ($row->{category} eq "Tithing") { $account_id = $accounts->{DSavings}->{id}; $row->{deducted} = 0 }
  $dsth->execute($account_id, $row->{category})
    or die $dsth->errstr;
  print "inserted account $account_id, $row->{category}\n";

  $dsth2->execute($allocation_setting_id, $allocation_amount, $row->{which}, $row->{deducted})
    or die $dsth->errstr;
  print "inserted $allocation_setting_id, $allocation_amount, $row->{which}, $row->{deducted}\n";
}
$sth->finish;
$dsth->finish;

my $categories = {};
$sth = $dbs->{dest}->{connection}->prepare("select * from categories where account = ?") or die $dbs->{source}->{connection}->errstr;;
for my $account (keys %$accounts) {
  $sth->execute($accounts->{$account}->{id}) or die $sth->errstr;
  while (my $row = $sth->fetchrow_hashref()) {
    $categories->{$row->{name}} = $row;
  } 
}
$sth->finish;

$dtrans = $dbs->{dest}->{connection}->prepare("
insert into transactions (stamp, date, entity, description, reconciled, transfer)
values (?, ?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$dalloc = $dbs->{dest}->{connection}->prepare("
insert into allocations (stamp, category, transaction, amount)
values (?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$dtag = $dbs->{dest}->{connection}->prepare("
insert into allocation_tag (allocation, tag)
values (?, ?)")
  or die $dbs->{source}->{connection}->errstr;;


$sth = $dbs->{source}->{connection}->prepare("select * from transactions where budgetname = 'bowman' order by date, to_from, subcategory desc, stamp") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
my $last = {};
my $last_id = undef;
print "Inserting transactions(X) and allocations(a) tag(t) (skipping beginning balance(-))";
while (my $row = $sth->fetchrow_hashref()) {
  if ($row->{date} =~ /^200[45678]-01-01$/ and $row->{subcategory} eq "Beginning Balance") {
    print "-";
    next;
  }
  next if ($row->{amount} == 0 and $row->{date} =~ /^2003-01-01$/);

  # transactions
  if ($row->{to_from} eq undef) { $row->{to_from} = "" }
  if ($row->{description} eq undef) { $row->{description} = "" }
  unless ($row->{subcategory} =~ /^Payday|Adjustment$/
      and $last->{subcategory} =~ /^Payday|Adjustment$/
      and ($row->{to_from} =~ /^SOS|ArosNet|America First|Original Amount|$/ and $row->{to_from} eq $last->{to_from})
      and ($row->{date} eq $last->{date})
      and $last_id ne undef) {
    my @params = ($row->{stamp}, $row->{date}, $row->{to_from}, $row->{description}, $row->{reconciled},
                  exists $categories->{$row->{to_from}} ? 1 : 0);
    unless ($dtrans->execute(@params)) {
      print "transaction: " . (join ",", @params) . "\n";
      die $dtrans->errstr;
    }
    print "X";
    ($last_id) = $dbs->{dest}->{connection}->selectrow_array("select id from transactions where id is null");
  }

  # allocations
  my @params = ($row->{stamp}, $categories->{$row->{category}}->{id}, $last_id, $row->{amount});
  unless ($dalloc->execute(@params)) {
    print "allocation: " . (join ",", @params) . "\n";
    die $dalloc->errstr;
  }
  my ($allocation_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocations where id is null");
  print "a";


  # tags
  my @tags = ();
  if ($row->{subcategory} =~ /^.*? and .*?$/ and $row->{subcategory} !~ /^Yard/) {
    for my $subcategory (split / and /, $row->{subcategory}) {
      if (exists $tag_ids{$subcategory}) {
        push @tags, $tag_ids{$subcategory}
      }
    }
  }
  else {
    @tags = ($tag_ids{$row->{subcategory}}) if exists $tag_ids{$row->{subcategory}};
  }

  for my $tag_id (@tags) {
    $dtag->execute($allocation_id, $tag_id);
    print "t"
  }

  $last = $row;
}
print "\n";
$sth->finish;
$dtrans->finish;
$dalloc->finish;



